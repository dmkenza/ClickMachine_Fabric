package com.kenza.clickmachine

import net.fabricmc.api.ClientModInitializer
import com.kenza.clickmachine.common.IRInventoryScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens

class ClickMachineClient : ClientModInitializer {
    override fun onInitializeClient() {

//        ScreenRegistry.register<AutoClickerGuiDescription, CottonInventoryScreen<AutoClickerGuiDescription>>(
//            GUI_SCREEN_HANDLER_TYPE
//        ) { description: AutoClickerGuiDescription?, inventory: PlayerInventory?, title: Text? ->
//            CottonInventoryScreen(
//                description,
//                inventory,
//                title
//            )
//        }

        HandledScreens.register(ClickMachine.GUI_SCREEN_HANDLER_TYPE) { controller, inv, _ -> IRInventoryScreen(controller, inv.player) }



    }
}