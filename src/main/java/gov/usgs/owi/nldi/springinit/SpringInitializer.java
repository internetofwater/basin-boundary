package gov.usgs.owi.nldi.springinit;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class SpringInitializer implements WebApplicationInitializer {

	/**
	 *  gets invoked automatically when application context loads
	 */
	public void onStartup(ServletContext servletContext) throws ServletException {		
		AnnotationConfigWebApplicationContext rootContext = new AnnotationConfigWebApplicationContext();
		rootContext.register(SpringConfig.class, JndiConfig.class, JmsConfig.class, MybatisConfig.class);
		servletContext.addListener(new ContextLoaderListener(rootContext));
	}

}