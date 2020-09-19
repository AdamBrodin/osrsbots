import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.osbot.rs07.api.Inventory;
import org.osbot.rs07.api.map.Area;
import org.osbot.rs07.api.map.Position;
import org.osbot.rs07.api.ui.RS2Widget;
import org.osbot.rs07.input.mouse.MiniMapTileDestination;
import org.osbot.rs07.script.Script;
import org.osbot.rs07.script.ScriptManifest;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@ScriptManifest(name = "HousePlankMaker", author = "adambrodin", version = 1.0, info = "Makes planks using the Demon Butler.", logo = "https://i.imgur.com/IO9a399.png")
public class HousePlankMaker extends Script {
	// IDS
	private static final int HOUSE_TELEPORT_TAB_ID = 8013, BANK_TELEPORT_TAB_ID = 8010, LOG_ITEM_ID = 6333,
			PLANK_ITEM_ID = 8780;

	// Expenses
	private static final int BUTLER_COST_PER_PLANK = 500, BUTLER_COST_PER_USAGE = 10000 / 8, BOT_BREAK_CHANCE = 95,
			BOT_BREAK_DURATION = 90000;
	private int HOUSE_TELEPORT_TAB_PRICE, BANK_TELEPORT_TAB_PRICE, LOG_ITEM_PRICE, PLANK_ITEM_PRICE;

	// GUI
	private static final int guiRectWidth = 580, showAllTimeStatsDuration = 60;
	private static int textXValue, textYValueStart, yValueIncrement = 25;
	private Font font;

	// Statistics
	private int planksInInv, logsInBank, inventoriesLeftBeforeFailure, totalPlanksMade, sessionNetProfit,
			sessionUptimeSeconds, gpPerHour, amountOfTrips, allTimeNetProfit = 0, allTimeUptimeSeconds = 0,
			allTimeAverageGpPerHour, timeUntilFailure;
	private RS2Widget houseOptions, callServant, invLeftBarWidget, settingsMenu;
	private Calendar calendar;
	private long startTimeMillis;
	private String expectedFailureTime = "CALCULATING...", currentBotStatus = "Idling...";

	// Etc
	private static final int standardRandomMinPause = 750, standardRandomMaxPause = 1250;
	private Area pvpZoneOutsideHouse;
	private Random rand;
	private Inventory inv;
	private Client socketClient;

	// IO
	final String filePath = getDirectoryData() + "HousePlankMaker.xml";

	private void LoadAllTimeStats() {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.parse(new File(filePath));
			doc.normalizeDocument();

			// Gets the root element (user in our case)
			Element user = doc.getDocumentElement();
			allTimeNetProfit = Integer.parseInt(user.getElementsByTagName("AllTimeNetProfit").item(0).getTextContent());
			allTimeUptimeSeconds = Integer
					.parseInt(doc.getElementsByTagName("AllTimeUpTimeSeconds").item(0).getTextContent());
			allTimeAverageGpPerHour = (allTimeNetProfit / allTimeUptimeSeconds) * 3600;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void SaveAllTimeStats() {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = builder.newDocument();

			Element user = doc.createElement("user");
			doc.appendChild(user);

			Attr attr = doc.createAttribute("id");
			attr.setValue("1");
			user.setAttributeNode(attr);

			Element allTimeNetProfitElement = doc.createElement("AllTimeNetProfit");
			allTimeNetProfitElement.setTextContent(Integer.toString(allTimeNetProfit));

			Element allTimeUptimeSecondsElement = doc.createElement("AllTimeUpTimeSeconds");
			allTimeUptimeSecondsElement.setTextContent(Integer.toString(allTimeUptimeSeconds));

			user.appendChild(allTimeNetProfitElement);
			user.appendChild(allTimeUptimeSecondsElement);

			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.transform(new DOMSource(doc), new StreamResult(new File(filePath)));
		} catch (Exception e) {
			e.printStackTrace();
			log("Save went wrong!!!!!");
			log(e.getMessage());
		}
	}

	@Override
	public void onStart() {
		log("HousePlankMaker - made by Adam Brodin");
		invLeftBarWidget = getWidgets().get(161, 34);
		settingsMenu = getWidgets().get(161, 42);
		pvpZoneOutsideHouse = new Area(2953, 3223, 2953, 3229);
		calendar = Calendar.getInstance();
		LoadAllTimeStats();
		startTimeMillis = System.currentTimeMillis();
		font = new Font("Consolas", Font.BOLD, 20);
		rand = new Random();
		inv = getInventory();
		GetItemPrices();
		socketClient = new Client();
		socketClient.SetAccountName(myPlayer().getName());

		if (pvpZoneOutsideHouse.contains(myPlayer())) {
			inv.getItem(BANK_TELEPORT_TAB_ID).interact("Break");
		}
	}

	private void GetItemPrices() {
		try {
			HOUSE_TELEPORT_TAB_PRICE = getGrandExchange().getOverallPrice(HOUSE_TELEPORT_TAB_ID);
			BANK_TELEPORT_TAB_PRICE = getGrandExchange().getOverallPrice(BANK_TELEPORT_TAB_ID);
			LOG_ITEM_PRICE = getGrandExchange().getOverallPrice(LOG_ITEM_ID);
			PLANK_ITEM_PRICE = getGrandExchange().getOverallPrice(PLANK_ITEM_ID);
		} catch (Exception e) {
			e.getStackTrace();
		}
	}

	private void RandomizedSleep() throws InterruptedException {
		int randSleepTime = (rand.nextInt(standardRandomMinPause) + 1) + standardRandomMaxPause;
		Thread.sleep(randSleepTime);
	}

	private void RandomizedSleep(int min, int max) throws InterruptedException {
		int randSleepTime = (rand.nextInt(max) + 1) + min;
		if (randSleepTime > 2000) {
			currentBotStatus = "IDLING...";
		}
		Thread.sleep(randSleepTime);
	}

	private void RandomizedSleep(int min, int max, boolean showIdleStatus) throws InterruptedException {
		int randSleepTime = (rand.nextInt(max) + 1) + min;
		if (randSleepTime > 2000 && showIdleStatus) {
			currentBotStatus = "IDLING...";
		}
		Thread.sleep(randSleepTime);
	}

	private void RandomizedHouseWalk() {
		currentBotStatus = "MOVING TO RANDOM LOCATION";
		Position randWalkPos = new Position(myPosition().getX() + (rand.nextInt(3) + 1) + -3,
				myPosition().getY() + (rand.nextInt(1) + 1), 2);
		MiniMapTileDestination tileSpot = new MiniMapTileDestination(getBot(), randWalkPos);
		getMouse().click(tileSpot);
	}

	private void EventOrder() throws InterruptedException {
		textXValue = invLeftBarWidget.getAbsX() - guiRectWidth;
		textYValueStart = invLeftBarWidget.getAbsY();

		// Bank Items
		BankItems();

		if (inv.getEmptySlots() <= 0 && !inv.contains(PLANK_ITEM_ID)) {
			// Teleports to player owned house
			currentBotStatus = "TELEPORTING TO PLAYER-OWNED HOUSE";
			inv.getItem(HOUSE_TELEPORT_TAB_ID).interact("Break");
			while (!getMap().isInHouse()) {
				RandomizedSleep(100, 200);
			}
			RandomizedHouseWalk();

			// Call Demon Butler
			CallButler();
			RandomizedSleep(1250, 1750);

			// Go through butler dialog
			ButlerDialogue();
			RandomizedSleep();
		}

		// Teleport to a bank
		currentBotStatus = "TELEPORTING TO BANK";
		inv.getItem(BANK_TELEPORT_TAB_ID).interact("Break");
		RandomizedSleep(2750, 3000, false);
		CalculateStats();
		CalculateExpectedFailureTime();
	}

	private void BankItems() throws InterruptedException {
		currentBotStatus = "BANKING ITEMS";

		// Prevent wasting time withdrawing items if they already exist in inventory
		if (!inv.contains(LOG_ITEM_ID)) {
			bank.open();
			if (!inv.contains("Coins")) {
				bank.withdrawAll("Coins");
			}
			RandomizedSleep();
		}

		// If an issue occurred which made the planks remain in the inventory
		if (inv.contains(PLANK_ITEM_ID)) {
			bank.depositAll(PLANK_ITEM_ID);
		}

		// Exits the bot if there are no logs left
		if (bank.contains(LOG_ITEM_ID) && bank.isOpen() && inv.contains("Coins")) {
			if (inv.getEmptySlots() > 0) {
				bank.withdrawAll(LOG_ITEM_ID);
				logsInBank = (int) bank.getAmount(LOG_ITEM_ID);
			}
		}

		RandomizedSleep();
		if (bank.isOpen()) {
			bank.close();
		}

		RandomizedSleep(500, 1000);
		planksInInv = (int) inv.getAmount(LOG_ITEM_ID);
	}

	private void CallButler() throws InterruptedException {
		currentBotStatus = "CALLING SERVANT";
		settingsMenu.interact();
		RandomizedSleep();

		houseOptions = getWidgets().get(261, 101);
		if (houseOptions != null) {
			houseOptions.interact();
		}

		RandomizedSleep();
		callServant = getWidgets().get(370, 19, 0);
		if (callServant != null) {
			callServant.interact();
		}
	}

	private void ShowChatbox() {
		RS2Widget chatboxWidget = getWidgets().get(162, 5);
		chatboxWidget.interact();
	}

	private void ButlerDialogue() throws InterruptedException {
		boolean dialogStartHasLogs;
		dialogStartHasLogs = inv.contains(LOG_ITEM_ID);

		currentBotStatus = "IN DIALOGUE WITH SERVANT";
		int timeBefore = sessionUptimeSeconds;
		while (getDialogues().inDialogue()) {
			getKeyboard().typeKey((char) KeyEvent.VK_1);
			RandomizedSleep();
			getKeyboard().pressKey(KeyEvent.VK_SPACE);
			RandomizedSleep();

			// If the bot gets stuck because the chatbox is hidden, show it again
			if (sessionUptimeSeconds - timeBefore > 15) {
				ShowChatbox();
				timeBefore = sessionUptimeSeconds;
			}
		}

		RandomizedSleep(250, 500);
		getKeyboard().pressKey((char) KeyEvent.VK_ESCAPE);

		if (dialogStartHasLogs && !inv.contains(LOG_ITEM_ID)) {
			totalPlanksMade += planksInInv;
			amountOfTrips++;
		}
	}

	@Override
	public int onLoop() throws InterruptedException {
		EventOrder();
		if (inventoriesLeftBeforeFailure <= 0 && sessionUptimeSeconds >= 90) {
			stop();
		}

		int randDelay = rand.nextInt(100);
		if (randDelay >= BOT_BREAK_CHANCE && sessionUptimeSeconds >= 300) {
			// Takes a break randomly based on a set chance (anti-ban measure)
			currentBotStatus = "TAKING A BREAK.....";
			return (int) (rand.nextInt(BOT_BREAK_DURATION) + 1) + BOT_BREAK_DURATION / 2;
		} else {
			return random(100, 500);
		}

	}

	private void CalculateStats() {
		int profitPerLog = (PLANK_ITEM_PRICE - LOG_ITEM_PRICE)
				- (BUTLER_COST_PER_PLANK - (BUTLER_COST_PER_USAGE / planksInInv));
		sessionNetProfit = (totalPlanksMade * profitPerLog)
				- ((HOUSE_TELEPORT_TAB_PRICE + BANK_TELEPORT_TAB_PRICE) * amountOfTrips);

		int gpPerSecond = sessionNetProfit / sessionUptimeSeconds;
		gpPerHour = gpPerSecond * 3600;

		int logInventoriesLeft = logsInBank / 25;
		int cashInventoriesLeft = (int) (inv.getAmount("Coins") / (BUTLER_COST_PER_PLANK * 25 + BUTLER_COST_PER_USAGE));
		int houseTeleportInventoriesLeft = (int) inv.getAmount(HOUSE_TELEPORT_TAB_ID);
		int bankTeleportInventoriesLeft = (int) inv.getAmount(BANK_TELEPORT_TAB_ID);

		int min = Math.min(logInventoriesLeft, cashInventoriesLeft);
		min = Math.min(min, houseTeleportInventoriesLeft);
		inventoriesLeftBeforeFailure = Math.min(min, bankTeleportInventoriesLeft);

		int averageTimePerInventory = sessionUptimeSeconds / amountOfTrips;
		timeUntilFailure = inventoriesLeftBeforeFailure * averageTimePerInventory;

		socketClient.SetProfitPerHour(gpPerHour);
		socketClient.SendMessage();
	}

	private String SecondsToTime(int inputSeconds) {
		int seconds = inputSeconds % 60;
		int minutes = (inputSeconds % 3600) / 60;
		int hours = inputSeconds / 3600;

		return Integer.toString(hours) + "h:" + Integer.toString(minutes) + "m:" + Integer.toString(seconds) + "s";
	}

	private void CalculateExpectedFailureTime() {
		calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, timeUntilFailure);
		expectedFailureTime = calendar.getTime().toString().substring(11, 20);
	}

	@Override
	public void onPaint(Graphics2D g) {
		long timeElapsed = System.currentTimeMillis() - startTimeMillis;
		sessionUptimeSeconds = (int) (timeElapsed) / 1000;
		int yValue = textYValueStart;

		g.setFont(font);
		g.setColor(Color.BLACK);
		g.drawRoundRect(textXValue - 25, textYValueStart - 25, guiRectWidth, 290, 50, 50);
		g.fillRoundRect(textXValue - 25, textYValueStart - 25, guiRectWidth, 290, 50, 50);
		g.setColor(Color.GREEN);
		g.drawString("CURRENT STATUS: " + currentBotStatus, textXValue, yValue);
		yValue += yValueIncrement;

		g.setColor(Color.WHITE);
		g.drawString("TIME ELAPSED: " + SecondsToTime(sessionUptimeSeconds), textXValue, yValue);
		yValue += yValueIncrement;

		g.setColor(Color.CYAN);
		g.drawString("TOTAL PLANKS MADE: " + totalPlanksMade, textXValue, yValue);
		yValue += yValueIncrement;

		g.setColor(Color.MAGENTA);
		g.drawString("LOGS LEFT IN BANK: " + logsInBank, textXValue, yValue);
		yValue += yValueIncrement;

		g.setColor(Color.RED);
		g.drawString("INVENTORIES REMAINING: " + inventoriesLeftBeforeFailure, textXValue, yValue);
		yValue += yValueIncrement;

		g.drawString("ESTIMATED COMPLETION TIME - " + expectedFailureTime, textXValue, yValue);
		yValue += yValueIncrement;

		g.setColor(Color.PINK);
		g.drawString("SESSION NET PROFIT: " + NumberFormat.getInstance(Locale.US).format(sessionNetProfit) + " GP",
				textXValue, yValue);
		yValue += yValueIncrement;

		g.setColor(Color.YELLOW);
		g.drawString("GP/HOUR: " + NumberFormat.getInstance(Locale.US).format(gpPerHour), textXValue, yValue);
		yValue += yValueIncrement;

		if (sessionUptimeSeconds <= showAllTimeStatsDuration) {
			g.setColor(Color.BLUE);
			g.drawString("ALL TIME NET PROFIT: " + NumberFormat.getInstance(Locale.US).format(allTimeNetProfit) + " GP",
					textXValue, yValue);
			yValue += yValueIncrement;

			g.drawString("ALL TIME UP TIME: " + SecondsToTime(allTimeUptimeSeconds), textXValue, yValue);
			yValue += yValueIncrement;
			g.drawString("ALL TIME AVERAGE: " + NumberFormat.getInstance(Locale.US).format(allTimeAverageGpPerHour)
					+ " GP/HOUR", textXValue, yValue);
		} else {
			yValueIncrement = 35;
		}

		// TODO
		/*
		 * g.DrawString("CURRENT STATUS" + currentBotStatus + "\nTIME ELAPSED: " +
		 * SecondsToTime(sessionUptimeSeconds) + "\nTOTAL PLANKS MADE: " +
		 * totalPlanksMade + "\nLOGS LEFT IN BANK: " + logsInBank +
		 * "\nINVENTORIES REMAINING: " + inventoriesLeftBeforeFailure +
		 * "\nESTIMATED COMPLETION TIME: " + expectedFailureTime +
		 * "\nSESSION NET PROFIT: " +
		 * NumberFormat.getInstance(Locale.US).format(sessionNetProfit) +
		 * "\nGP PER HOUR: " + NumberFormat.getInstance(Locale.US).format(gpPerHour) +
		 * "\nALL TIME NET PROFIT: " + allTimeNetProfit + "\nALL TIME UP TIME: " +
		 * SecondsToTime(allTimeUptimeSeconds) + "\nALL TIME AVERAGE: " +
		 * allTimeAverageGpPerHour, textXValue, yValue);
		 */

	}

	@Override
	public void onExit() {
		if (sessionNetProfit > 0) {
			allTimeNetProfit += sessionNetProfit;
		} else {
			log("sessionNetProfit < 0, current Value: " + sessionNetProfit);
		}

		if (sessionUptimeSeconds > 0) {
			allTimeUptimeSeconds += sessionUptimeSeconds;
		} else {
			log("sessionUptimeSeconds < 0, current Value: " + sessionUptimeSeconds);
		}

		SaveAllTimeStats();
		log("Session ended with a total net profit of: " + Integer.toString(sessionNetProfit) + " - Runtime: "
				+ SecondsToTime(sessionUptimeSeconds));
	}

}
