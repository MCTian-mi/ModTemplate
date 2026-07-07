package com.myname.mymodid;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Tags.MOD_ID)
public class MyMod {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_ID);

    public MyMod() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegister);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("{} common setup.", Tags.MOD_NAME);
    }

    private void onRegister(final RegisterEvent event) {
        // Register items, blocks, etc. here
    }
}
