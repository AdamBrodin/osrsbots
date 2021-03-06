import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class StatsIO {
	// IO
	private String filePath = "HousePlankMaker.xml";
	private HousePlankMaker housePlankMaker;

	public StatsIO(HousePlankMaker housePlankMaker) {
		this.housePlankMaker = housePlankMaker;
	}

	public void Start() {
		filePath = housePlankMaker.getDirectoryData() + filePath;
		LoadAllTimeStats();
	}

	public void LoadAllTimeStats() {
		try {
			final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			final Document doc = builder.parse(new File(filePath));
			doc.normalizeDocument();

			// Gets the root element (user in our case)
			final Element user = doc.getDocumentElement();
			housePlankMaker.allTimeNetProfit = Integer
					.parseInt(user.getElementsByTagName("AllTimeNetProfit").item(0).getTextContent());
			housePlankMaker.allTimeUptimeSeconds = Integer
					.parseInt(doc.getElementsByTagName("AllTimeUpTimeSeconds").item(0).getTextContent());
			housePlankMaker.allTimeAverageGpPerHour = (housePlankMaker.allTimeNetProfit
					/ housePlankMaker.allTimeUptimeSeconds) * 3600;
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public void SaveAllTimeStats() {
		try {
			final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			final Document doc = builder.newDocument();

			final Element user = doc.createElement("user");
			doc.appendChild(user);

			final Attr attr = doc.createAttribute("id");
			attr.setValue("1");
			user.setAttributeNode(attr);

			final Element allTimeNetProfitElement = doc.createElement("AllTimeNetProfit");
			allTimeNetProfitElement.setTextContent(Integer.toString(housePlankMaker.allTimeNetProfit));

			final Element allTimeUptimeSecondsElement = doc.createElement("AllTimeUpTimeSeconds");
			allTimeUptimeSecondsElement.setTextContent(Integer.toString(housePlankMaker.allTimeUptimeSeconds));

			user.appendChild(allTimeNetProfitElement);
			user.appendChild(allTimeUptimeSecondsElement);

			final Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			tr.transform(new DOMSource(doc), new StreamResult(new File(filePath)));
		} catch (final Exception e) {
			e.printStackTrace();
			housePlankMaker.log("Save went wrong!!!!!");
			housePlankMaker.log(e.getMessage());
		}
	}
}
