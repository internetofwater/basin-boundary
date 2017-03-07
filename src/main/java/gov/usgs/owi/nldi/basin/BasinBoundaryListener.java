package gov.usgs.owi.nldi.basin;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.NumberUtils;

@Service
public class BasinBoundaryListener implements MessageListener {
	private static final Logger LOG = LoggerFactory.getLogger(BasinBoundaryListener.class);

	private static int minArea;
	private static int maxArea;
	private static int step;
	private static String[] region;

	private Dao dao;

	@Autowired
	public BasinBoundaryListener(Dao inDao) {
		dao = inDao;
	}

	@Override
	public void onMessage(Message message) {
		LOG.info("***** begin message ingest *****");
		long start = System.currentTimeMillis();
		String msgText = "";

		try {
			msgText = ((TextMessage) message).getText();
			String[] args = msgText.split(",");
			if (args.length < 4) {
				LOG.error("This program requires 3 arguments: minArea, maxArea, and step!");
			} else {
				minArea = NumberUtils.parseNumber(args[0], Integer.class);
				maxArea = NumberUtils.parseNumber(args[1], Integer.class);
				step = NumberUtils.parseNumber(args[2], Integer.class);
				if (args.length == 4) {
					switch (args[3]) {
					case "05":
					case "06":
					case "07":
					case "08":
					case "10":
					case "11":
						LOG.info("Everything drained by the Mississippi (05, 06, 07, 08, 10, and 11) is grouped together.");
						region = new String[]{"05", "06", "07", "08", "10", "11"};
						break;
					case "14":
					case "15":
						LOG.info("The entire Colorado (14 and 15) is done as one unit.");
						region = new String[]{"14", "15"};
						break;
					default:
						LOG.info(args[3] + " can be done by itself.");
						region = new String[]{args[3]};
						break;
					}
				}
			}
		} catch (NumberFormatException e) {
			LOG.error("Invalid ID given in the JMS Message:" + msgText);
		} catch (Throwable e) {
			LOG.error("Something Bad Happened:", e);
		}

		calculateBasins(minArea, maxArea, step);
		LOG.info("***** end message ingest (" + (System.currentTimeMillis() - start) + " ms) *****");
	}

	private void calculateBasins(int minArea, int maxArea, int step) {
		LOG.info("Entering calculateBasins with:" + minArea + ":" + maxArea + ":" + step + ".");
		int stepMinArea = minArea;
		while (stepMinArea + step <= maxArea) {
			int stepMaxArea = stepMinArea + step;
			LOG.info("About to process areas between " + stepMinArea + " and " + stepMaxArea);

			dao.truncateBasinTemp();

			long start = System.currentTimeMillis();
			int calculated = dao.buildBasins(stepMinArea, stepMaxArea, region);
			LOG.info("Calculated basin for " + calculated + " Huc12s in " + (System.currentTimeMillis() - start) + " ms");

			int copied = dao.copyBasins();
			LOG.info("Copied data for " + copied + " basins.");

			int updated = dao.updateStartFlags();
			LOG.info("Updated " + updated + " startflags.");

			stepMinArea = stepMaxArea;
		}
		
	}

}
