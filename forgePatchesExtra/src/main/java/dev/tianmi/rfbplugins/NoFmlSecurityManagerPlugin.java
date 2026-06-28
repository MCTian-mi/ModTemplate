package dev.tianmi.rfbplugins;

import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;

public class NoFmlSecurityManagerPlugin implements RfbPlugin {
    private static final RfbClassTransformer[] TRANSFORMERS = {
            new FmlTweakerSecurityManagerTransformer()
    };

    @Override
    public RfbClassTransformer[] makeTransformers() {
        return TRANSFORMERS;
    }
}

