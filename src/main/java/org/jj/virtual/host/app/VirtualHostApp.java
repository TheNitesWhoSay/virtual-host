package org.jj.virtual.host.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.GenericFilterBean;

@SpringBootApplication
@ComponentScan("org.jj.virtual.host")
public class VirtualHostApp extends GenericFilterBean {
	
	private static final Logger log = LoggerFactory.getLogger(VirtualHostApp.class);

	@Resource(name="domainForwarding") 
	private Map<String, String> domainForwarding;
	
	@Resource(name="domainPorts") 
	private Map<String, Integer> domainPorts;

    public static void main(String[] args) {
        SpringApplication.run(VirtualHostApp.class, args);
    }

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
			throws IOException, ServletException {
		
		if ( servletRequest instanceof HttpServletRequest ) {

			HttpServletRequest httpRequest = (HttpServletRequest)servletRequest;
			
			String originalDomainName = servletRequest.getServerName();
			Integer originalPort = servletRequest.getLocalPort();
			
			String domainName = originalDomainName;
			Integer port = originalPort;
			if ( originalDomainName != null ) {
				String destinationDomain = domainForwarding.get(domainName);
				if ( destinationDomain != null ) {
					domainName = destinationDomain;
				}
				Integer destinationPort = domainPorts.get(domainName);
				if ( destinationPort != null ) {
					port = destinationPort;
				}
			}
			
			if ( port != null ) {
				try {
					URI uri = new URI("http", null, domainName, port, httpRequest.getRequestURI(), httpRequest.getQueryString(), null);
					
					HttpEntity<String> requestEntity = null;
					String bodyContents = null;
					BufferedReader bodyReader = httpRequest.getReader();
					if ( bodyReader != null && bodyReader.ready() ) {
						bodyContents = IOUtils.toString(bodyReader);
						if ( bodyContents != null && !bodyContents.isEmpty() ) {
							HttpHeaders requestHeaders = new HttpHeaders();
							Enumeration<String> headerNames = httpRequest.getHeaderNames();
							while ( headerNames.hasMoreElements() ) {
								String headerName = headerNames.nextElement();
								if ( headerName != null && !headerName.isEmpty() ) {
									String headerValue = httpRequest.getHeader(headerName);
									requestHeaders.add(headerName, headerValue);
								}
							}
							if ( !requestHeaders.isEmpty() ) {
								requestEntity = new HttpEntity<String>(bodyContents, requestHeaders);
							} else {
								requestEntity = new HttpEntity<String>(bodyContents);
							}
						}
					}
					
					HttpMethod method = HttpMethod.valueOf(httpRequest.getMethod());
					
					RestTemplate restTemplate = new RestTemplate();
					byte[] response = restTemplate.exchange(uri, method, requestEntity, byte[].class).getBody();
					if ( response != null && response.length > 0 ) {
						servletResponse.getOutputStream().write(response);
					}
	
				} catch (URISyntaxException e) {
					log.error("URISyntaxException ", e);
				} catch (HttpClientErrorException e) {
					throw e;
				} catch (Exception e) {
					log.error("Exception ", e);
				} finally {
					String logMessage = new StringBuilder("Sent ")
							.append(originalDomainName != null ? originalDomainName : "null")
							.append(":").append(originalPort != null ? originalPort : "null")
							.append(" to ").append(domainName != null ? domainName : "null")
							.append(":").append(port != null ? port : "null").toString();
					
					log.info(logMessage);
				}
			}
			
		}
	}
    
}
