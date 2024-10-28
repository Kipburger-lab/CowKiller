import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.utilities.Logger;

@ScriptManifest(name = "KLUS_CowKiller", description = "Kills cows, loots the hides and than banks them.", author = "KLUS",
        version = 0.1, category = Category.COMBAT, image = "")
public class Mainclass extends AbstractScript {

    @Override
    public int onLoop() {
        Logger.log("My first script!");
        return 1000;
    }

}