import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

	private final static int socketPort = 7132;
	private static Socket soc;
	private static String accountName;
	private static int totalNetProfitPerHour;

	public void SetupSocket() {
		try {
			soc = new Socket("localhost", socketPort);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void SendMessage() {
		try {
			SetupSocket();
			String message = accountName + " is active with a net profit/hour of: " + totalNetProfitPerHour + "gp";
			final PrintWriter pw = new PrintWriter(soc.getOutputStream());
			pw.write(message);
			pw.flush();
			pw.close();
			soc.close();
		} catch (final Exception e) {
			System.out.println("OOO SEND MESSAGE GONE HELLA WRONG: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void SetProfitPerHour(int profit) {
		totalNetProfitPerHour = profit;
	}

	public void SetAccountName(final String name) {
		accountName = name;
	}
}
