package com.kenza.clickmachine.blocks

import blue.endless.jankson.annotation.Nullable
import com.google.common.base.Preconditions
import com.kenza.clickmachine.ClickMachine.Companion.FAKE_PLAYER_BUILDER
import com.kenza.clickmachine.GuiMod
import com.kenza.clickmachine.common.UpdateAutoClickerPacket
import com.kenza.clickmachine.utils.toVec3d
import io.netty.buffer.Unpooled
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.block.AirBlock
import net.minecraft.util.math.BlockPos
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.boss.WitherEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.util.collection.DefaultedList
import net.minecraft.item.ItemStack
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.LiteralText
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.nbt.NbtCompound
import net.minecraft.inventory.Inventories
import net.minecraft.item.SwordItem
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.network.ServerPlayerInteractionManager
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

class AutoClickerBlockEntity(pos: BlockPos?, state: BlockState?) :
    ImplementedInventory, BlockEntity(GuiMod.GUI_BLOCKENTITY_TYPE, pos, state),
    NamedScreenHandlerFactory {


    private val fakePlayer by lazy {
        FAKE_PLAYER_BUILDER.create(
            world!!.server,
            world as ServerWorld,
            "AutoClickerBlockEntity"
        )
    }

    var rightClickMode = false
        set(value) {
            tickCounter = 0
            field = value
            markDirty()
        }

    private var items = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY)
    private var tickCounter = 0

    private val interactionManager: ServerPlayerInteractionManager?
        get() {
            return MinecraftClient.getInstance().server?.getPlayerInteractionManager(fakePlayer)
        }


    override fun markDirty() {
        super<BlockEntity>.markDirty()
    }

    override fun canPlayerUse(player: PlayerEntity): Boolean {
        return pos.isWithinDistance(player.blockPos, 4.5)
    }

    override fun getItems(): DefaultedList<ItemStack> {
        return items
    }

    override fun getDisplayName(): Text {
        return LiteralText("Auto Clicker")
    }

    @Nullable
    override fun createMenu(syncId: Int, inv: PlayerInventory, player: PlayerEntity): ScreenHandler? {
        return AutoClickerGuiDescription(
            syncId,
            inv,
            ScreenHandlerContext.create(world, pos)
        )
    }


    override fun toUpdatePacket(): BlockEntityUpdateS2CPacket {
        return BlockEntityUpdateS2CPacket.create(this)
    }

    override fun toInitialChunkDataNbt(): NbtCompound {
        val nbt = super.toInitialChunkDataNbt()
        writeNbt(nbt)
//        nbt.putBoolean("#c", true)
        return nbt
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        Inventories.readNbt(nbt, items)
        rightClickMode = nbt.getBoolean("rightClickMode")
    }

    public override fun writeNbt(nbt: NbtCompound) {
        Inventories.writeNbt(nbt, items)
        nbt.putBoolean("rightClickMode", rightClickMode)
    }



    fun tick(facing: Direction) {

        if (world?.isClient == true) return

        val itemStack = items.firstOrNull()

        fakePlayer.updateLastActionTime()


        (fakePlayer as ServerPlayerEntity).tick()

        if (itemStack == null || itemStack.isEmpty) {
            fakePlayer.inventory.clear()
            return
        }

        if(fakePlayer.inventory.isEmpty){
            fakePlayer.inventory.selectedSlot = 0
            fakePlayer.inventory.addPickBlock(itemStack)
        }


        val blockPos = pos.offset(facing)
        val block = world?.getBlockState(blockPos)

        val source = DamageSource.player(fakePlayer)
        val mobs = world?.getEntitiesByClass(LivingEntity::class.java, Box(blockPos)) { e -> e !is PlayerEntity && e !is ArmorStandEntity && !e.isDead && !e.isInvulnerableTo(source) && (e !is WitherEntity || e.invulnerableTimer <= 0) } ?: emptyList()

        tickCounter++

        if (block?.isAir == true) {


            world?.setBlockBreakingInfo(fakePlayer.id, blockPos, -1)

            if(mobs.isEmpty()){
                tickCounter = 0
                return
            }

            if(rightClickMode){
                tickRightModeForEntity(itemStack, mobs, facing)
            }else {
                tickLeftModeForEntity(itemStack, mobs, source)
            }

            return
        }


        if (rightClickMode) {
            tickRightMode(itemStack, blockPos, facing)
        } else {
            tickLeftMode(itemStack, blockPos)
        }

//        fakePlayer.inventory.clear()
    }


    private fun tickLeftModeForEntity(itemStack: ItemStack, mobs: List<LivingEntity>, source: DamageSource) {

        if(tickCounter >= 20){
            tickCounter = 0
            mobs.firstOrNull()?.let { mob ->

//                itemStack.damage(1, world?.random, null)
//
//                if (mob.isAlive) {
//                    mob.damage(source, (itemStack.damage ).toFloat())
//                }

//                fakePlayer.itemCooldownManager.set(itemStack.item, 200)
//                fakePlayer.handleAttack(mob)
                fakePlayer.itemCooldownManager.set(itemStack.item, 1)
                fakePlayer.tryAttack(mob)
                fakePlayer.attack(mob)
                fakePlayer.itemCooldownManager.set(itemStack.item, 1)
            }


        }


    }

    private fun tickRightModeForEntity(itemStack: ItemStack, mobs: List<LivingEntity>, facing: Direction) {

        if(tickCounter >= 20){
            tickCounter = 0
            mobs.firstOrNull().let { mob ->
                fakePlayer.interact(mob, Hand.MAIN_HAND)

            }
        }
    }

    private fun tickRightMode(itemStack: ItemStack, blockPos: BlockPos, facing: Direction) {
        fakePlayer.interactAt(fakePlayer, blockPos.toVec3d(), Hand.MAIN_HAND)
        world?.setBlockBreakingInfo(fakePlayer.id, blockPos, -1)
        val d1 = interactionManager?.interactBlock(
            fakePlayer,
            world,
            itemStack,
            Hand.MAIN_HAND,
            BlockHitResult(blockPos.toVec3d(), Direction.UP, blockPos, false)
        )
    }


    private fun tickLeftMode(itemStack: ItemStack, blockPos: BlockPos) {

        val stateToBreak: BlockState = world!!.getBlockState(blockPos)
        val blockHardness: Float = stateToBreak.getHardness(world, blockPos)

        if (!canBreak(stateToBreak, blockHardness)) {
            tickCounter = 0
            world?.setBlockBreakingInfo(fakePlayer.id, blockPos, -1)
            return
        }

        val breakSpeed = (itemStack.item)?.getMiningSpeedMultiplier(itemStack, stateToBreak) ?: 0.1f
        val destroyProgress = tickCounter * 0.001 * (breakSpeed / blockHardness) * 150

        if (destroyProgress >= 10) {
            interactionManager?.tryBreakBlock(blockPos)
            fakePlayer.inventory.clear()
            tickCounter = 0
            world?.setBlockBreakingInfo(fakePlayer.id, blockPos, -1)
            return
        }

        world?.setBlockBreakingInfo(fakePlayer.id, blockPos, destroyProgress.toInt())
    }

    private fun canBreak(stateToBreak: BlockState, blockHardness: Float): Boolean {
        return isBreakable(stateToBreak, blockHardness)
    }

    private fun isBreakable(stateToBreak: BlockState, blockHardness: Float): Boolean {
        return !(stateToBreak.material.isLiquid || stateToBreak.block is AirBlock
                || blockHardness == -1f)
    }


    open fun sync() {
        Preconditions.checkNotNull(world) // Maintain distinct failure case from below
        check(world is ServerWorld) { "Cannot call sync() on the logical client! Did you check world.isClient first?" }
        (world as ServerWorld).chunkManager.markForUpdate(getPos())
    }

    companion object {
        const val INVENTORY_SIZE = 1

        fun sendValueUpdatePacket(value: Boolean, ctx: ScreenHandlerContext) {
            val packet = PacketByteBuf(Unpooled.buffer())
            packet.writeBoolean(value)
            var x1: BlockPos? = null
            ctx.run { _, pos ->
                x1 = pos
                packet.writeBlockPos(pos)
            }
            ClientPlayNetworking.send(UpdateAutoClickerPacket.UPDATE_VALUE_PACKET_ID, packet)
        }
    }

}