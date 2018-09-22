package com.mstar.baa.spider;

import static com.mstar.baa.utilities.BAASpiderUtility.nullOrZero;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * 
 * @author Sameer Gaware
 *
 */
public abstract class BAASeleniumSpider extends BAABaseSpider {

	private String chromeDriverPath = null;  
	private WebDriver driver = null;
	private WebDriver webDrivers[] = new WebDriver[10]; 
	private Drivers currentDriver = null;
	private BlockingQueue<String> linksToVisit = null;
	private Queue<String> dataLinkQueue = null; 
	private String mainPageBody = null;
	List<String> allLinks = null;
	private int threadCount = 1;
	private ChromeOptions options = null;
	Object cacheCountLock = new Object();
	private File cacheFile = null;

	public BAASeleniumSpider() {
		currentDriver = Drivers.HEADLESSCHROME;
	}

	@Override
	public void setName(String name) {
		NAME = name;
	}

	/**
	 * 
	 * @param currentDriver
	 */
	public void setDriver(Drivers currentDriver)
	{
		this.currentDriver = currentDriver;
	}

	@Override
	public String getBody(String url) {
		driver.get(startLink);

		String body = driver.getPageSource();
		mainPageBody = body;
		return body;
	}

	@Override
	public void preparePhase()  {

		try {

			linksToVisit = new ArrayBlockingQueue<>(1000);
			linksToVisit.add(startLink); 
			allLinks = new CopyOnWriteArrayList<>();
			dataLinkQueue = new ConcurrentLinkedQueue<String>();
			cacheFile = getCacheDirectory();
			linkToCache = new ConcurrentSkipListMap<>();
			visitedLinks = new ConcurrentSkipListSet<>();

			if(!cacheFile.exists())
				cacheFile.mkdir();

			System.out.println("Inside Headless chrome");

			//TODO : find better way to have dynamic path
			chromeDriverPath = "E:\\Chrome Driver\\chromedriver.exe" ;  
			System.setProperty("webdriver.chrome.driver", chromeDriverPath);  
			options = new ChromeOptions();
			options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200","--ignore-certificate-errors");

			driver = new ChromeDriver(options);

			for(int index = 1 ; index<=threadCount ; index++) {
				webDrivers[index-1] = new ChromeDriver(options);
			}

		}	catch(Exception  e) {

		}
	}


	@Override
	public void postPhase(String url, String body) {
		// TODO Auto-generated method stub

	}


	@Override
	public List<String> extractURLPhase(String url, String body) {
		// TODO Auto-generated method stub
		return extractURLPhase(url, body,null);
	}


	public List<String> extractURLPhase(String url, String body, WebDriver webDrivers2) {

		List<String> links = new ArrayList<>();

		if(webDrivers2 == null)
			return links;

		if(skipExtractLinkPhase)
			return links; 

		if(!setExtractLinkFromDataPage && isDataPage(url))
			return links;

		webDrivers2.get(url); 
		System.out.println("Extracting link from : "+url); 

		List<WebElement> allElements = webDrivers2.findElements(By.tagName("a"));
		if(!nullOrZero(allElements)) {
			for(WebElement we : allElements) {
				String link = we.getAttribute("href");
				link = normalizeURL(link);

				if(nullOrZero(link))
					continue;
				if((isDataPage(link) || isTraversalPage(link)) && !visitedLinks.contains(link)) {
					links.add(link);
					visitedLinks.add(link);
					System.out.println("	"+link); 
				}
			}
		}
		return links;
	}

	private void extractURLPhase() throws InterruptedException {
		for(int index = 0 ; index < threadCount ; index++) {
			WebDriver wdr = webDrivers[index]; 

			executorService.submit(() -> {
				while(!linksToVisit.isEmpty()) {
					String link = linksToVisit.peek();
					System.out.println("Thread : "+Thread.currentThread()); 
					List<String> links = extractURLPhase(link, null, wdr);
					if(!nullOrZero(links)) {
						List<String> traversalLinks = links.stream().filter(element -> isTraversalPage(element)).collect(Collectors.toList());
						List<String> dataLinks = links.stream().filter(element1 -> isDataPage(element1)).collect(Collectors.toList());

						dataLinks.stream().forEach(element1 -> linkToCache.put(element1, "")); 

						linksToVisit.addAll(traversalLinks);
					}
					System.out.println("Removing "+link+" from queue "+linksToVisit.size()+" "); 
					linksToVisit.poll();
				}
			});
		}

		executorService.shutdown();
		executorService.awaitTermination(1,TimeUnit.DAYS); 
		System.out.println("Shutting down executor service Caught "+linkToCache.size()+" data pages");
		//Testing
		for(String element : linkToCache.keySet() )
		{
			System.out.println(" linK : "+element); 

		}
	}

	@Override
	public void start(String startLink, int threadCount) {

		this.startLink = startLink;
		this.threadCount = threadCount;
		executorService = Executors.newFixedThreadPool(threadCount);

		switch (currentDriver) {
		case HEADLESSCHROME: {

			try {
				preparePhase();
				extractURLPhase();
				mainPhase();

				System.out.println("Finished Extract link phase");

			}catch (Exception e) {
				e.printStackTrace();
			}
		}

		break;
		case CHROME: {

		}
		break;
		}
	}

	private void mainPhase() throws InterruptedException, IOException {

		//add data links in dataLinkQueue for processing
		executorService = Executors.newFixedThreadPool(threadCount);
		dataLinkQueue.addAll(linkToCache.keySet());


		for(int index = 0 ; index < threadCount ; index++) {

			WebDriver wdr = webDrivers[index]; 

			executorService.submit(() -> {
				while(!dataLinkQueue.isEmpty()) {
					String link = dataLinkQueue.poll();

					wdr.get(link);
					String pageSource = wdr.getPageSource();

					try {
						int currentCacheCount = ++cacheCount;
						Files.write(Paths.get(cacheFile.getAbsolutePath(),"/"+currentCacheCount+".html"), pageSource.getBytes());
						linkToCache.put(link, currentCacheCount+".html"); 

					} catch (IOException e) {
						e.printStackTrace();
					}
					

					System.out.println("Main Phase using "+Thread.currentThread().getName()+" ` "+link+" remaining "+dataLinkQueue.size()+" "); 

				} 
				//wdr.close();
			});

		}

		executorService.shutdown();
		executorService.awaitTermination(1,TimeUnit.DAYS);

		BufferedWriter br = new BufferedWriter(new FileWriter(new File(cacheFile, "cache.dat"))); 


		for(String key : linkToCache.keySet()) {

			br.write(key+"`"+linkToCache.get(key)); 
			br.newLine();
		}
		br.flush();
		br.close();
		System.out.println("Shutting down executor service Caught "+linkToCache.size()+" data pages"); 
		System.out.println("Finished main Phase");  
	}
}
