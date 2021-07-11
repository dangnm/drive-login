package com.syncinator.kodi.login.oauth.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ehcache.Cache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpStatusCodeException;

import com.syncinator.kodi.login.KodiLoginCacheManager;
import com.syncinator.kodi.login.controller.ReportController;
import com.syncinator.kodi.login.model.Pin;
import com.syncinator.kodi.login.oauth.provider.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/callback")
public class CallbackController {

	@Autowired
	private ApplicationContext context;
	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private ReportController reportController;
	
	@RequestMapping
	public String callback(
			@RequestParam(required=false) String code,
			@RequestParam(required=false) String state,
			@RequestParam(required=false, name="session_state") String sessionState,
			@RequestParam(required=false) String error,
			@RequestParam(required=false, name="error_description") String errorDescription,
			Model model) throws Exception {
		logger.info("=== Wilber === STARTING CALLBACK");
		if (error == null) {
			logger.info("=== Wilber === STARTING CALLBACK WITHOUT ERROR");
			Cache<String, Pin> pinCache = KodiLoginCacheManager.getPinCache();
			if (state != null) {
				String pin = state.toLowerCase();
				Pin storedPin = pinCache.get(pin);
				logger.info("=== Wilber === STARTING CALLBACK WITH storedPin");
				if (storedPin != null) {
					logger.info("=== Wilber === STARTING CALLBACK PROCESSING storedPin");
					Provider connector = context.getBean(Provider.NAME_PREFIX + storedPin.getProvider(), Provider.class);
					Map<String,Object> tokens = connector.tokens(Provider.GRANT_TYPE_AUTHORIZATION_CODE, code);
					logger.info("=== Wilber === getting tokens");
					if (tokens != null) {
						// logger.info("=== Wilber === getting tokens OK " + tokens);
						storedPin.setAccessToken(tokens);
						return "redirect:auth-success";
					} else {
						logger.info("=== Wilber === getting tokens FAILED");
						model.addAttribute("errorCode", "failure.code.2");
					}
				} else {
					model.addAttribute("errorCode", "failure.code.1");
				}
			} else {
				model.addAttribute("errorCode", "failure.code.3");
			}
		} else {
			logger.info("=== Wilber === STARTING CALLBACK HAVING ERROR");
			model.addAttribute("errorText", error + ": " + errorDescription);
		}
		return "auth-failure";
	}
	
	@ExceptionHandler(HttpStatusCodeException.class)
	public String httpClientErrorExceptionHandler(Model model, HttpServletResponse response, HttpStatusCodeException e) throws IOException{
		model.addAttribute("errorText", e.getResponseBodyAsString());
		return "auth-failure";
	}
	
	@ExceptionHandler(Exception.class)
	public String httpClientErrorExceptionHandler(Model model, HttpServletRequest request, HttpServletResponse response, Exception e) throws IOException{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		e.printStackTrace(ps);
		try {
			reportController.reportError(baos.toString(), request);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		model.addAttribute("errorText", e.getMessage());
		return "auth-failure";
	}
}
