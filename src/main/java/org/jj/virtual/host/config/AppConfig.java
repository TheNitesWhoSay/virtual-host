package org.jj.virtual.host.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

	public Properties getProperties(String resourcePath) throws IOException {
		InputStream resourceStream = AppConfig.class.getClassLoader().getResourceAsStream(resourcePath);
		try {
			Properties properties = new Properties();
			properties.load(resourceStream);
			return properties;
		} finally {
			resourceStream.close();
		}
	}
	
	public Map<String, String> getDomainForwarding() throws IOException {
		Map<String, String> domainForwarding = new HashMap<String, String>();
		Properties domainForwardingProps = getProperties("domainForwarding.properties");
		if ( domainForwardingProps != null ) {
			Enumeration<?> forwardedDomains = domainForwardingProps.propertyNames();
			while ( forwardedDomains.hasMoreElements() ) {
				String forwardedDomain = (String)forwardedDomains.nextElement();
				String destinationDomain = domainForwardingProps.getProperty(forwardedDomain);
				domainForwarding.put(forwardedDomain, destinationDomain);
			}
		}
		return domainForwarding;
	}
	
	public Map<String, Integer> getDomainPorts() throws IOException {
		Map<String, Integer> domainPorts = new HashMap<String, Integer>();
		Properties domainPortProps = getProperties("domainPorts.properties");
		if ( domainPortProps != null ) {
			Enumeration<?> domains = domainPortProps.propertyNames();
			while ( domains.hasMoreElements() ) {
				String domain = (String)domains.nextElement();
				Integer port = Integer.parseInt(domainPortProps.getProperty(domain));
				domainPorts.put(domain, port);
			}
		}
		return domainPorts;
	}
	
	@Bean
	public Map<String, String> domainForwarding() throws IOException {
		return getDomainForwarding();
	}
	
	@Bean
	public Map<String, Integer> domainPorts() throws IOException {
		return getDomainPorts();
	}
	
}
