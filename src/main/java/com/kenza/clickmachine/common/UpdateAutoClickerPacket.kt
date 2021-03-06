package com.kenza.clickmachine.common

import com.kenza.clickmachine.blocks.AutoClickerBlockEntity
import com.kenza.clickmachine.utils.identifier
import com.kenza.clickmachine.utils.isLoaded
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

object UpdateAutoClickerPacket  {

    val UPDATE_VALUE_PACKET_ID = identifier("update_value_packet") 

     fun register() {
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_VALUE_PACKET_ID) { server, player, _, buf, _ ->
            val value = buf.readBoolean()
            val pos = buf.readBlockPos()
            val world = player.world
            server.execute {
                if (world.isLoaded(pos)) {
                    val blockEntity = world.getBlockEntity(pos) as? AutoClickerBlockEntity ?: return@execute
                    blockEntity.rightClickMode = value
                    blockEntity.markDirty()
                    blockEntity.sync()
                }
            }
        }
    }
}