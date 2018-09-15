package com.mstar.WebCrawler;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
	public static void main(String argsp[])
	{
		System.out.println("Inside Headless chrome");

		String chromeDriverPath = "E:\\Chrome Driver\\chromedriver.exe" ;  
		System.setProperty("webdriver.chrome.driver", chromeDriverPath);  
		ChromeOptions options = new ChromeOptions();
		//options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200","--ignore-certificate-errors");

		WebDriver driver = new ChromeDriver(options);

		while(true) {
			Thread t = new Thread(() -> {driver.get("https://www.globalviews.com/"); driver.findElements(By.tagName("a"));});
			t.start();
		}

	}

}
