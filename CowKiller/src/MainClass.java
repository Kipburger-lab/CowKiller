import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.methods.input.mouse.MouseSettings;
import org.dreambot.api.input.Mouse;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@ScriptManifest(name = "CowKiller Pro", description = "Kills cows in Lumbridge", author = "AuthorName", version = 1.0, category = Category.COMBAT)
public class MainClass extends AbstractScript {

    private final Area COW_AREA = new Area(3243, 3298, 3263, 3282);
    private final Area LUMBRIDGE_BANK_AREA = new Area(3205, 3209, 3210, 3216, 2);
    private final Area LUMBRIDGE_CASTLE_AREA = new Area(3201, 3208, 3219, 3231, 0);
    private final Tile FIRST_STAIR_TILE = new Tile(3205, 3209, 0);
    private final int FIRST_STAIR_ID = 16671;
    private final int SECOND_STAIR_ID = 16672;

    private final int COWHIDE_ID = 1739;
    private final int COWHIDE_VALUE = 300;
    private State currentState = State.WALKING_TO_COWS;
    private BankingState bankingState = BankingState.WALKING_TO_STAIRS;
    private int totalCowhides = 0;
    private Instant startTime;
    private Random random = new Random();

    private enum BankingState {
        WALKING_TO_STAIRS,
        CLIMBING_FIRST_STAIRS,
        CLIMBING_SECOND_STAIRS,
        WALKING_TO_BANKER
    }

    @Override
    public void onStart() {
        startTime = Instant.now();
        log("CowKiller Pro started!");
        MouseSettings.setSpeed(random.nextInt(10) + 7);
    }

    private boolean isLootNearby() {
        GroundItem cowhide = GroundItems.closest(item ->
                item != null &&
                        item.getID() == COWHIDE_ID &&
                        item.getTile().distance(Players.getLocal().getTile()) <= 15
        );
        return cowhide != null;
    }

    private boolean shouldBank() {
        return Inventory.isFull() ||
                (Inventory.contains(COWHIDE_ID) && LUMBRIDGE_CASTLE_AREA.contains(Players.getLocal()));
    }

    private boolean handleBanking() {
        switch (bankingState) {
            case WALKING_TO_STAIRS:
                if (Players.getLocal().distance(FIRST_STAIR_TILE) > 3) {
                    Walking.walk(FIRST_STAIR_TILE);
                    randomizeMouseMovement();
                    return false;
                }
                bankingState = BankingState.CLIMBING_FIRST_STAIRS;
                return false;

            case CLIMBING_FIRST_STAIRS:
                GameObject firstStairs = GameObjects.closest(obj ->
                        obj != null &&
                                obj.getID() == FIRST_STAIR_ID
                );

                if (firstStairs != null && firstStairs.interact("Climb-up")) {
                    Sleep.sleepUntil(() -> Players.getLocal().getZ() == 1, 5000);
                    bankingState = BankingState.CLIMBING_SECOND_STAIRS;
                }
                return false;

            case CLIMBING_SECOND_STAIRS:
                GameObject secondStairs = GameObjects.closest(obj ->
                        obj != null &&
                                obj.getID() == SECOND_STAIR_ID
                );

                if (secondStairs != null && secondStairs.interact("Climb-up")) {
                    Sleep.sleepUntil(() -> Players.getLocal().getZ() == 2, 5000);
                    bankingState = BankingState.WALKING_TO_BANKER;
                }
                return false;

            case WALKING_TO_BANKER:
                if (!Bank.isOpen()) {
                    if (Bank.open()) {
                        return true;
                    }
                }
                return false;
        }
        return false;
    }

    private void randomizeMouseMovement() {
        if (random.nextInt(100) < 5) {
            Point currentPos = Mouse.getPosition();
            Point randomPoint = new Point(
                    currentPos.x + (random.nextInt(300) - 150),
                    currentPos.y + (random.nextInt(300) - 150)
            );
            Mouse.move(randomPoint);
            Sleep.sleep(random.nextInt(500) + 250);

            if (random.nextInt(100) < 20) {
                MouseSettings.setSpeed(random.nextInt(10) + 7);
            }
        }
    }

    @Override
    public int onLoop() {
        // Check for banking need first
        if (shouldBank() && currentState != State.BANKING) {
            currentState = State.WALKING_TO_BANK;
            return 600;
        }

        // Check for loot if not in combat
        if (!Players.getLocal().isInCombat() && isLootNearby() &&
                currentState != State.LOOTING && currentState != State.BANKING &&
                currentState != State.WALKING_TO_BANK) {
            currentState = State.LOOTING;
            return 600;
        }

        switch (currentState) {
            case WALKING_TO_COWS:
                if (!shouldBank()) {
                    if (!COW_AREA.contains(Players.getLocal())) {
                        Walking.walk(COW_AREA.getRandomTile());
                        randomizeMouseMovement();
                    } else {
                        if (isLootNearby()) {
                            currentState = State.LOOTING;
                        } else {
                            currentState = State.ATTACKING_COW;
                        }
                    }
                } else {
                    currentState = State.WALKING_TO_BANK;
                }
                break;

            case ATTACKING_COW:
                if (!shouldBank() && !isLootNearby()) {
                    NPC cow = NPCs.closest(npc ->
                            npc != null &&
                                    npc.getName().equals("Cow") &&
                                    !npc.isInCombat() &&
                                    COW_AREA.contains(npc)
                    );

                    if (cow != null && cow.interact("Attack")) {
                        Sleep.sleepUntil(() -> Players.getLocal().isInCombat(), 5000);
                        currentState = State.IN_COMBAT;
                    }
                } else if (!Players.getLocal().isInCombat()) {
                    if (isLootNearby()) {
                        currentState = State.LOOTING;
                    } else if (shouldBank()) {
                        currentState = State.WALKING_TO_BANK;
                    }
                }
                break;

            case IN_COMBAT:
                if (!Players.getLocal().isInCombat()) {
                    currentState = State.WAITING_FOR_LOOT;
                }
                break;

            case WAITING_FOR_LOOT:
                Sleep.sleep(600, 800);
                currentState = State.LOOTING;
                break;

            case LOOTING:
                if (!shouldBank()) {
                    GroundItem cowhide = GroundItems.closest(item ->
                            item != null &&
                                    item.getID() == COWHIDE_ID
                    );

                    if (cowhide != null && cowhide.interact("Take")) {
                        Sleep.sleepUntil(() -> !cowhide.exists(), 5000);
                        totalCowhides++;
                    }

                    if (isLootNearby()) {
                        currentState = State.LOOTING;
                    } else if (shouldBank()) {
                        currentState = State.WALKING_TO_BANK;
                    } else {
                        currentState = State.ATTACKING_COW;
                    }
                } else {
                    currentState = State.WALKING_TO_BANK;
                }
                break;

            case WALKING_TO_BANK:
                if (handleBanking()) {
                    currentState = State.BANKING;
                }
                break;

            case BANKING:
                if (Bank.isOpen()) {
                    Bank.depositAll(COWHIDE_ID);
                    Bank.close();
                    bankingState = BankingState.WALKING_TO_STAIRS;
                    currentState = State.WALKING_TO_COWS;
                }
                break;

            case IDLE:
                break;
        }
        return 600;
    }

    @Override
    public void onPaint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
        g2d.setColor(new Color(0, 0, 0, 180));

        // Adjusted Y position (60px lower)
        int baseY = 329;
        g2d.fillRect(5, baseY, 509, 129);

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        GradientPaint gradient = new GradientPaint(
                0, baseY, new Color(0, 191, 255),
                0, baseY + 20, new Color(30, 144, 255)
        );
        g2d.setPaint(gradient);
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2d.drawString("CowKiller Pro", 10, baseY + 21);

        g2d.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawString("by AuthorName", 150, baseY + 21);

        Duration runtime = Duration.between(startTime, Instant.now());
        String runtimeStr = String.format("%02d:%02d:%02d",
                runtime.toHours(),
                runtime.toMinutesPart(),
                runtime.toSecondsPart());

        g2d.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g2d.setColor(Color.WHITE);
        g2d.drawString("Current State: " + currentState, 10, baseY + 41);
        g2d.drawString("Total Cowhides: " + totalCowhides, 10, baseY + 61);
        g2d.drawString("Total Value: " + (totalCowhides * COWHIDE_VALUE) + " gp", 10, baseY + 81);
        g2d.drawString("Runtime: " + runtimeStr, 10, baseY + 101);

        if (currentState == State.WALKING_TO_BANK || currentState == State.BANKING) {
            g2d.drawString("Banking State: " + bankingState, 10, baseY + 121);
        }
    }

    @Override
    public void onExit() {
        log("CowKiller Pro stopped!");
    }
}
