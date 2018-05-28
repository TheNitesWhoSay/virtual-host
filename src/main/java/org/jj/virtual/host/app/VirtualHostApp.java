package org.jj.virtual.host.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.GenericFilterBean;

@SpringBootApplication
@ComponentScan("org.jj.virtual.host")
public class VirtualHostApp extends GenericFilterBean {
	
	private static final Logger log = LoggerFactory.getLogger(VirtualHostApp.class);

	private static final int defaultHttpPort = 80;
	
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
		
		if ( servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse ) {

			HttpServletRequest httpRequest = (HttpServletRequest)servletRequest;
			HttpServletResponse httpResponse = (HttpServletResponse)servletResponse;
			
			String originalDomainName = httpRequest.getServerName();
			int originalPort = httpRequest.getLocalPort();
			String remoteAddress = httpRequest.getRemoteAddr();
			String remoteHost = httpRequest.getRemoteHost();
			int remotePort = httpRequest.getRemotePort();
			
			boolean hasOriginalDomainName = originalDomainName != null && !originalDomainName.isEmpty();
			boolean hasRemoteAddress = remoteAddress != null && !remoteAddress.isEmpty();
			boolean hasRemoteHost = remoteHost != null && !remoteHost.isEmpty();
			
			boolean isIpv6 = remoteAddress != null && remoteAddress.contains(":");
			boolean fromDefaultHttpPort = remotePort == defaultHttpPort;
			
			//log.info("Proxy hit from: " + (originalDomainName != null ? originalDomainName : "null") + ":" + (originalPort != null ? originalPort : "null"));
			
			String domainName = originalDomainName;
			Integer port = originalPort;
			if ( hasOriginalDomainName ) {
				String destinationDomain = domainForwarding.get(domainName);
				if ( destinationDomain != null ) {
					domainName = destinationDomain;
				}
				Integer destinationPort = domainPorts.get(domainName);
				if ( destinationPort != null ) {
					port = destinationPort;
				}
			}
			
			//log.info("Attempting forward to: " + (domainName != null ? domainName : "null") + ":" + (port != null ? port : "null"));
			
			if ( port != null ) {
				URI uri = null;
				try {
					uri = new URI("http", null, domainName, port, httpRequest.getRequestURI(), httpRequest.getQueryString(), null);
					
					HttpEntity<String> requestEntity = null;
					HttpHeaders requestHeaders = new HttpHeaders();
					
					String bodyContents = null;
					BufferedReader bodyReader = httpRequest.getReader();
					if ( bodyReader != null ) {
						try {
							if ( bodyReader != null && bodyReader.ready() ) {
								bodyContents = IOUtils.toString(bodyReader);
							}
						} finally {
							bodyReader.close();
						}
					}

					Enumeration<String> headerNames = httpRequest.getHeaderNames();
					if ( headerNames != null ) {
						while ( headerNames.hasMoreElements() ) {
							String headerName = headerNames.nextElement();
							if ( headerName != null && !headerName.isEmpty() ) {
								String headerValue = httpRequest.getHeader(headerName);
								switch ( headerName ) {
									case "Forwarded": break;
									case "X-Forwarded-For": break;
									case "X-Forwarded-Host": break;
									case "X-Forwarded-Proto": break;
									default: requestHeaders.add(headerName, headerValue); break;
								}
							}
						}
					}

					StringBuilder forwardedBuilder = new StringBuilder();
					if ( hasRemoteAddress ) {
						requestHeaders.add("X-Forwarded-For", remoteAddress);
						forwardedBuilder.append("for=");
						if ( isIpv6 || !fromDefaultHttpPort ) { // Source address is ipv6 or port is included, need to quote
							forwardedBuilder.append("\"[").append(remoteAddress).append(']');
							if ( !fromDefaultHttpPort ) {
								forwardedBuilder.append(':').append(remotePort);
							}
							forwardedBuilder.append('\"');
						} else {
							forwardedBuilder.append(remoteAddress);
						}
						forwardedBuilder.append("; ");
					}
					forwardedBuilder.append("proto=http");
					requestHeaders.add("X-Forwarded-Proto", "http");
					if ( hasRemoteHost ) {
						forwardedBuilder.append("; host=").append(remoteHost);
					}
					requestHeaders.add("Forwarded", forwardedBuilder.toString());
					
					if ( hasOriginalDomainName ) {
						requestHeaders.add("X-Forwarded-Host", new StringBuilder(originalDomainName).append(':').append(originalPort).toString());
					}
					
					if ( bodyContents != null ) {
						if ( !requestHeaders.isEmpty() ) {
							requestEntity = new HttpEntity<String>(bodyContents, requestHeaders);
						} else {
							requestEntity = new HttpEntity<String>(bodyContents);
						}
					} else if ( !requestHeaders.isEmpty() ) {
						requestEntity = new HttpEntity<String>(requestHeaders);
					}
					
					HttpMethod method = HttpMethod.valueOf(httpRequest.getMethod());
					
					Future<Void> forwarder = forwardRequest(httpResponse, uri, method, requestEntity);
					forwarder.get(300, TimeUnit.SECONDS);
					
				} catch (URISyntaxException e) {
					log.error("URISyntaxException ", e);
					httpResponse.setStatus(HttpStatus.NOT_FOUND.value());
					httpResponse.setContentType(MediaType.ALL.toString());
					httpResponse.setContentLength(0);
				} catch (IllegalArgumentException e) {
					log.error("IllegalArgumentException ", e);
					httpResponse.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
					httpResponse.setContentType(MediaType.ALL.toString());
					httpResponse.setContentLength(0);
				} catch (HttpStatusCodeException e) {
					HttpHeaders httpHeaders = e.getResponseHeaders();
					if ( httpHeaders != null ) {
						MediaType mediaType = httpHeaders.getContentType();
						if ( mediaType != null ) {
							httpResponse.setContentType(mediaType.toString());
						}
					}
					HttpStatus httpStatus = e.getStatusCode();
					if ( httpStatus != null ) {
						httpResponse.setStatus(httpStatus.value());
					}
					byte[] responseBody = e.getResponseBodyAsByteArray();
					if ( responseBody != null && responseBody.length > 0 ) {
						httpResponse.setContentLength(responseBody.length);
						httpResponse.getOutputStream().write(responseBody);
					} else {
						httpResponse.setContentLength(0);
					}
					log.error("Exception ", e);
				} catch (Exception e) {
					log.error("Unknown exception ", e);
					httpResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
					httpResponse.setContentType(MediaType.ALL.toString());
					httpResponse.setContentLength(0);
				} finally {
					chain.doFilter(httpRequest, httpResponse);
					httpResponse.flushBuffer();
					String logMessage = new StringBuilder("Sent ")
							.append(originalDomainName != null ? originalDomainName : "null")
							.append(":").append(originalPort)
							.append(" to ").append(domainName != null ? domainName : "null")
							.append(":").append(port != null ? port : "null").toString();
					
					log.info(logMessage);
				}
			}
			
		}
	}
    
	@Async
	public Future<Void> forwardRequest(HttpServletResponse httpResponse, URI uri, HttpMethod method, HttpEntity<String> requestEntity) throws IOException {
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<byte[]> response = restTemplate.exchange(uri, method, requestEntity, byte[].class);
		if ( response != null ) {
			HttpHeaders httpHeaders = response.getHeaders();
			if ( httpHeaders != null ) {
				MediaType mediaType = httpHeaders.getContentType();
				if ( mediaType != null ) {
					httpResponse.setContentType(mediaType.toString());
				}
			}
			HttpStatus httpStatus = response.getStatusCode();
			if ( httpStatus != null ) {
				httpResponse.setStatus(httpStatus.value());
			}
			byte[] responseBody = response.getBody();
			if ( responseBody != null && responseBody.length > 0 ) {
				httpResponse.setContentLength(responseBody.length);
				httpResponse.getOutputStream().write(responseBody);
			}
		}
		return new AsyncResult<Void>(null);
	}
	
}
