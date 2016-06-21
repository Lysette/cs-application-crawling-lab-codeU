package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Node;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;
	
	// the index where the results go
	private JedisIndex index;
	
	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();
	
	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 * 
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 * 
	 * @return
	 */
	public int queueSize() {
		return queue.size();	
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b 
	 * 
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
        // TODO
		
		// 1. get url from queue
		String url = queue.poll();
		
		if (testing) {

			try {
				// 2. read page using wikifetcher.readwikipedia
				Elements paragraphs = wf.readWikipedia(url);
				
				// 3. index page
				index.indexPage(url, paragraphs);
				
				// 4. add internal links to end of queue
				queueInternalLinks(paragraphs);
			}
			catch (IOException e) {
				throw e;
			}
			
			// 5. return url
			return url;
			
		}
		else {
			// 2. check if url is already indexed, if yes return null, else index it
			if (index.isIndexed(url)){
				return null;
			}
			else {
				
				try {
					// 3. read page using wikifetcher.readwikipedia
					Elements paragraphs = wf.readWikipedia(url);
				
					// 4. index page
					index.indexPage(url, paragraphs);
					
					// 5. add links to queue
					queueInternalLinks(paragraphs);
					
					// 6. return url
					return url;
				}
				catch (IOException e) {
					throw e;
				}
			}
		}
	}
	
	/**
	 * Parses paragraphs and adds internal links to the queue.
	 * 
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	void queueInternalLinks(Elements paragraphs) {
        // TODO
		
		String urlStart = "https://en.wikipedia.org";
		
		// go thru each paragraph
		for (Element para : paragraphs) { 
			Iterable<Node> iter = new WikiNodeIterable(para);
	
			// go thru each node in each paragraph
			for (Node node : iter) {
				// check if node is link
				if (node instanceof Element && ((Element) node).tagName().equals("a")) {
					
					String urlEnd = ((Element) node).attr("href");
					
					// wikipedia link
					if (urlEnd.startsWith("/wiki/") ) {
						// add url to queue
						String url = urlStart + urlEnd;
						queue.add(url);
					}
					
				}
			}
		}
		
	}

	public static void main(String[] args) throws IOException {
		
		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);
		
		index.deleteTermCounters();
		index.deleteURLSets();
		index.deleteAllKeys();
		
		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

			//TODO
            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            //break;
		} while (res == null);
		
		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}
	}
}
