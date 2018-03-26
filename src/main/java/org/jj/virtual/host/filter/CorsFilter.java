package org.jj.virtual.host.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

@Component
public class CorsFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if ( response instanceof HttpServletResponse ) {
			HttpServletResponse httpResponse = (HttpServletResponse)response;
			httpResponse.setHeader("Access-Control-Allow-Origin", "*");
			httpResponse.setHeader("x-frame-options", "allow-from *");
			httpResponse.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT, PATCH");
			httpResponse.setHeader("Access-Control-Allow-Headers", "authorization, x-requested-with, content-type");
		}
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		
	}	
}
