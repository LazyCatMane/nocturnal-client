package nocturnal.features.render;

import nocturnal.module.Category;
import nocturnal.module.Module;
import nocturnal.setting.BoolSetting;
import nocturnal.setting.NumberSetting;

/** Read by the ClickGUI when it draws its background; has no listeners of its own. */
public class GuiBlur extends Module {
    public final BoolSetting blur = add(new BoolSetting("Blur", "Blur the game; strength follows Options > Accessibility > Menu Background Blur", true));
    public final NumberSetting dim = add(new NumberSetting("Dim", "How much to darken the game", 0.35, 0, 0.9, 0.05));

    public GuiBlur() {
        super("GuiBlur", "Blurs and dims the game behind the ClickGUI", Category.RENDER);
        enabledByDefault();
    }
}
