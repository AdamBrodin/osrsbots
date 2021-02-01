package com.adambrodin.ultrapotion;

import org.dreambot.api.Client;
import org.dreambot.api.data.GameState;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.bank.BankMode;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.input.Keyboard;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.quest.Quest;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.wrappers.widgets.WidgetChild;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.net.URL;

@ScriptManifest(category = Category.HERBLORE, name = "UltraPotion", description = "Makes potions for a nice profit.", author = "Adam Brodin", version = 1.0)
public class Bot extends AbstractScript {
    private PotionLevel[] potions;
    private int potionIndex, inventoriesCompleted, firstItemBankAmount, secondItemBankAmount;
    private final int textYOffset = 30;
    private Image uiImage;

    @Override
    public void onStart() {
        potions = new PotionLevel[]
                {
                        // CRAFTED POTION ID IS NOTED FORM
                        new PotionLevel(3, new int[]{91, 221}, new String[]{"Guam potion (unf)", "Eye of newt"}, 121, 9, "Attack potion(3)"),
                        new PotionLevel(5, new int[]{93, 235}, new String[]{"Marrentill potion (unf)", "Unicorn horn dust"}, 175, 32, "Antipoison(3)"),
                        new PotionLevel(12, new int[]{95, 225}, new String[]{"Tarromin potion (unf)", "Limpwurt root"}, 115, 236, "Strength potion(3)"),
                        new PotionLevel(30, new int[]{257, 227}, new String[]{"Ranarr weed", "Vial of water"}, 99, "Ranarr potion (unf)")
                };

        sleepUntil(() -> Client.getGameState() == GameState.LOGGED_IN, 30000); // Wait until the player is logged to function properly
        if (Quests.isFinished(Quest.DRUIDIC_RITUAL)) {
            try {
                uiImage = ImageIO.read(new URL("https://i.imgur.com/utiuN6I.png"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            GetCurrentPotionLevel();
            logInfo("Starting potion level: " + potionIndex + " - " + potions[potionIndex].craftedPotionName);

            if (GrandExchange.isOpen()) {
                GrandExchange.close();
                sleepUntil(() -> !GrandExchange.isOpen(), 5000);
            }
        } else {
            logError("Cannot continue, please complete the quest Druidic Ritual!");
        }
    }

    @Override
    public int onLoop() {
        EventLoop();
        return 0;
    }

    private void EventLoop() {
        GetCurrentPotionLevel();

        // If the required items to make a potion aren't in the inventory
        if (!Inventory.contains(potions[potionIndex].combineItemIds[0]) || !Inventory.contains(potions[potionIndex].combineItemIds[1]) || Inventory.count(potions[potionIndex].combineItemIds[0]) < 14 || Inventory.count(potions[potionIndex].combineItemIds[1]) < 14) {
            BankItems();
        }

        MakePotion();
    }

    private void MakePotion() {
        if (Bank.isOpen()) {
            Bank.close();
            sleepUntil(() -> !Bank.isOpen(), 5000);
        }

        if (Inventory.contains(potions[potionIndex].combineItemIds[0]) && Inventory.contains(potions[potionIndex].combineItemIds[1])) {
            if (Inventory.count(potions[potionIndex].combineItemIds[0]) == 14 &&
                    Inventory.count(potions[potionIndex].combineItemIds[1]) == 14) {
                Inventory.get(potions[potionIndex].combineItemIds[0]).interact("Use");
                sleepUntil(Inventory::isItemSelected, 5000);
                sleep(1000);
                Inventory.get(potions[potionIndex].combineItemIds[1]).interact("Use");
                sleepUntil(() -> Widgets.getWidgetChild(270, 14) != null, 2000);
                sleep(250);
                if (Widgets.getWidgetChild(270, 14) != null) {
                    //Widgets.getWidgetChild(270, 14).interact("Make");
                    Keyboard.type(" ");
                }
                sleepUntil(() -> Inventory.count(potions[potionIndex].craftedPotionID) == 14, 30000);
                inventoriesCompleted++;
                logInfo("Finished making 14x " + potions[potionIndex].craftedPotionName + "!");
            }
        }
    }

    private void GetCurrentPotionLevel() {
        // Starts from the highest level requirement and checks if the player has sufficient level to perform it
        for (int i = potions.length - 1; i >= 0; i--) {
            if (Skills.getRealLevel(Skill.HERBLORE) >= potions[i].levelRequirement) {
                potionIndex = i;
                return;
            }
        }
    }

    private void OpenBank() {
        if (NPCs.closest("Banker") == null && !Bank.isOpen()) {
            // Backup bank opening method
            Bank.openClosest();
            sleepUntil(() -> NPCs.closest("Banker") != null || Bank.isOpen(), 10000);

            if (!Bank.isOpen()) {
                logError("No bank/banker was found, please move to the GrandExchange! Exiting....");
                stop();
            }
        } else if (NPCs.closest("Banker") != null && !Bank.isOpen()) {
            NPCs.closest("Banker").interact("Bank");
            logInfo("Opening bank");
            sleepUntil(Bank::isOpen, 10000);
        } else {
            logError("No bank/banker was found (2), please move to the GrandExchange! Exiting....");
            stop();
        }

    }

    private void BankItems() {
        if (!Bank.isOpen()) {
            OpenBank();
        }
        logInfo("Depositing all items");
        Bank.depositAllItems();
        sleepUntil(() -> Inventory.getEmptySlots() == 28, 5000);
        if (Bank.count(potions[potionIndex].combineItemIds[0]) >= 14 && Bank.count(potions[potionIndex].combineItemIds[1]) >= 14) {
            if (Bank.getWithdrawMode() != BankMode.ITEM) {
                logInfo("Changing withdraw mode to ITEM");
                Bank.setWithdrawMode(BankMode.ITEM);
                sleepUntil(() -> Bank.getWithdrawMode() == BankMode.ITEM, 5000);
            }

            logInfo("Withdrawing items used to create potion");
            Bank.withdraw(potions[potionIndex].combineItemIds[0], 14);
            sleepUntil(() -> Inventory.count(potions[potionIndex].combineItemIds[0]) == 14, 5000);
            Bank.withdraw(potions[potionIndex].combineItemIds[1], 14);
            sleepUntil(() -> Inventory.count(potions[potionIndex].combineItemIds[1]) == 14, 5000);
            Bank.close();
            sleepUntil(() -> !Bank.isOpen(), 5000);
        } else {
            // Buy more of the required items;
            logInfo("Not enough items in bank, restocking");
            //Bank.close();
            //sleepUntil(() -> !Bank.isOpen(), 5000);
            firstItemBankAmount = Bank.count(potions[potionIndex].combineItemIds[0]);
            secondItemBankAmount = Bank.count(potions[potionIndex].combineItemIds[1]);
            log("Found " + firstItemBankAmount + "x " + potions[potionIndex].combineItemNames[0] + " & " + secondItemBankAmount + "x " + potions[potionIndex].combineItemNames[1] + " in bank");
            RestockItems();
        }
    }

    private void RestockItems() {
        if (Inventory.contains("Coins") && Bank.contains("Coins")) {
            Bank.withdrawAll("Coins");
            sleepUntil(() -> Inventory.get("Coins").getAmount() > 0, 5000);
        } else if (!Inventory.contains("Coins") && Bank.contains("Coins")) {
            Bank.withdrawAll("Coins");
            sleepUntil(() -> Inventory.contains("Coins"), 5000);
        } else {
            logInfo("No coins found in inventory/bank, attempting to sell.");
        }

        // Withdraw items to sell
        for (PotionLevel p : potions) {
            if (Bank.contains(p.craftedPotionID)) {
                if (Bank.getWithdrawMode() != BankMode.NOTE) {
                    logInfo("Changing withdraw mode to NOTE");
                    Bank.setWithdrawMode(BankMode.NOTE);
                    sleepUntil(() -> Bank.getWithdrawMode() == BankMode.NOTE, 5000);
                }
                Bank.withdrawAll(p.craftedPotionID);
                sleepUntil(() -> Inventory.contains(p.craftedPotionID), 5000);
            }
        }

        Bank.close();
        sleepUntil(() -> !Bank.isOpen(), 5000);

        // Closes the bank and moves on to the GrandExchange
        if (NPCs.closest("Grand Exchange Clerk") == null) {
            logError("GrandExchange not found, please move to the GrandExchange! Quitting...");
            stop();
        }

        logInfo("Opening GrandExchange interface");
        NPCs.closest("Grand Exchange Clerk").interact("Exchange");
        sleepUntil(GrandExchange::isOpen, 5000);

        SellFinishedPotions();
        PurchaseSupplies();
    }

    private void CollectGEItems() {
        logInfo("Checking if ready to collect items (waiting up to 30 seconds extra)");
        sleepUntil(GrandExchange::isReadyToCollect, 30000);
        if (GrandExchange.isReadyToCollect()) {
            GrandExchange.collect();
        } else {
            logError("Couldn't collect any items!");
        }
        sleep(500);
    }

    private void SellFinishedPotions() {
        int itemsToSell = 0;
        for (PotionLevel p : potions) {
            if (Inventory.contains(p.craftedPotionName)) {
                log("Fetching price for: " + p.craftedPotionName);
                int price = GrandExchangeAPI.getPricedItem(p.craftedPotionID).getSellAverage();
                sleepUntil(() -> price > 0, 10000);
                log("Price of " + p.craftedPotionName + " is: " + price + "gp");
                logInfo(p.craftedPotionName + " price is: " + price);
                int amountToSell = Inventory.count(p.craftedPotionID + 1);
                GrandExchange.sellItem(p.craftedPotionName, Inventory.count(p.craftedPotionID + 1), (int) (price * 0.9));
                logInfo("Sell offer for " + amountToSell + "x " + p.craftedPotionName + " created!");
                sleepUntil(() -> GrandExchange.slotContainsItem(0), 30000);
                sleep(3000);
                CollectGEItems();
                itemsToSell++;
            }
        }

        if (itemsToSell <= 0) {
            logError("No items to sell were found in inventory!");
        }
    }

    private void PurchaseSupplies() {
        if (!GrandExchange.isOpen()) {
            if (NPCs.closest("Grand Exchange Clerk") == null) {
                logError("GrandExchange not found, please move to the GrandExchange! Quitting...");
                stop();
            }

            NPCs.closest("Grand Exchange Clerk").interact("Exchange");
            sleepUntil(GrandExchange::isOpen, 10000);
            sleep(1000);
        }

        if (!Inventory.contains("Coins")) {
            logError("No coins found in inventory, exiting...");
            stop();
        }

        sleepUntil(() -> !GrandExchange.isReadyToCollect(), 5000);
        int amountOfCash = Inventory.get("Coins").getAmount();
        logInfo("Amount of coins: " + amountOfCash);

        //if (amountOfCash >= (GrandExchangeAPI.getPrice(potions[potionIndex].combineItemIds[0]) * 14) + (GrandExchangeAPI.getPrice(potions[potionIndex].combineItemIds[0]) * 14)) {
        if (amountOfCash >= 100000) {
            // The max amount of inventories that the money is sufficient for
            int costPerInventory = 0;
            for (int i = 0; i <= potions[potionIndex].combineItemIds.length - 1; i++) {
                logInfo("Looking up price for ID: " + potions[potionIndex].combineItemIds[i]);
                int price = GrandExchangeAPI.getPricedItem(potions[potionIndex].combineItemIds[i]).getBuyAverage();
                sleepUntil(() -> price > 0, 10000);
                logInfo("Price for ID" + potions[potionIndex].combineItemIds[0] + " (" + potions[potionIndex].combineItemNames[1] + ") is: " + price);
                costPerInventory += (int) (price * 1.1) * 14;
            }

            int purchasableInventories = (int) amountOfCash / costPerInventory;

            // Making sure no unnecessary supplies are bought (more than needed before next potion level)
            if (purchasableInventories > potions[potionIndex].potionsRequiredUntilNext && potions[potionIndex].potionsRequiredUntilNext != 0) {
                purchasableInventories = potions[potionIndex].potionsRequiredUntilNext;
            }

            logInfo("Purchasing " + purchasableInventories + "x inventories worth of supplies for " + potions[potionIndex].craftedPotionName
                    + "supplies (" + potions[potionIndex].combineItemNames[0] + " & " + potions[potionIndex].combineItemNames[1] + ")");

            // Purchases each necessary item
            for (int i = 0; i <= potions[potionIndex].combineItemIds.length - 1; i++) {
                log("Looking up price for ID: " + potions[potionIndex].combineItemIds[i]);
                sleep(2000);
                int price = GrandExchangeAPI.getPricedItem(potions[potionIndex].combineItemIds[i]).getBuyAverage();
                sleepUntil(() -> price > 0, 10000);
                sleep(1000);
                if (i == 0) {
                    int amountToBuy = (purchasableInventories * 14) - firstItemBankAmount;
                    if (amountToBuy <= 0) {
                        log("Amount to buy for i: 0 is: " + amountToBuy);
                        continue;
                    }
                    if (100 * (secondItemBankAmount - firstItemBankAmount) <= amountOfCash && (secondItemBankAmount - firstItemBankAmount) >= 28) { // TODO FIX
                        amountToBuy = secondItemBankAmount - firstItemBankAmount;
                    }
                    GrandExchange.buyItem(potions[potionIndex].combineItemNames[i], amountToBuy, (int) (price * 1.1));
                } else if (i == 1) {
                    int amountToBuy = (purchasableInventories * 14) - secondItemBankAmount;
                    if (amountToBuy <= 0) {
                        log("Amount to buy for i: 1 is: " + amountToBuy);
                        continue;
                    }

                    if (100 * (firstItemBankAmount - secondItemBankAmount) <= amountOfCash && (firstItemBankAmount - secondItemBankAmount) >= 28) { // TODO FIX
                        amountToBuy = firstItemBankAmount - secondItemBankAmount;
                    }

                    // GrandExchange.buyItem(potions[potionIndex].combineItemNames[i], amountToBuy, (int) (price * 1.1));
                    GrandExchange.buyItem(potions[potionIndex].combineItemNames[i], amountToBuy, 100); // TODO FETCH PRICE
                }
                log("Price for ID" + potions[potionIndex].combineItemIds[i] + " (" + potions[potionIndex].combineItemNames[i] + ") is: " + price);
                sleepUntil(() -> GrandExchange.slotContainsItem(0), 5000);
                sleep(3000);
                CollectGEItems();
            }

            logInfo("Closing GrandExchange UI");
            GrandExchange.close();
            sleepUntil(() -> !GrandExchange.isOpen(), 5000);
            sleep(1000);
        } else {
            //logError("Amount of money (" + amountOfCash + ") is too low for a single inventory of supplies which cost "
            //    + (amountOfCash >= (GrandExchangeAPI.getPrice(potions[potionIndex].combineItemIds[0]) * 14) + (GrandExchangeAPI.getPrice(potions[potionIndex].combineItemIds[0]) * 14)) + " exiting..");
            stop();
        }

    }

    @Override
    public void onPaint(Graphics2D g) {
        Font font = new Font("Consolas", Font.PLAIN, 25);
        WidgetChild chatBoxWidget = Widgets.getWidgetChild(162, 0);
        int heightOffset = 100;
        int x = chatBoxWidget.getX();
        int y = chatBoxWidget.getY() - heightOffset;
        int width = chatBoxWidget.getWidth();
        int height = chatBoxWidget.getHeight() + heightOffset;
        g.drawImage(uiImage, x, y, width, height, null);

        g.setColor(Color.WHITE);
        g.setFont(font);
        g.drawString("INVENTORIES COMPLETED: " + inventoriesCompleted, x + 5, y + textYOffset);
    }


}
