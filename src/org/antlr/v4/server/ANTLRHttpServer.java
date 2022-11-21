package org.antlr.v4.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ANTLRHttpServer {
	public static final String IMAGES_DIR = "/tmp/antlr-images";

	public static void main(String[] args) throws Exception {
		new File(IMAGES_DIR).mkdirs();

		Files.createDirectories(Path.of("/var/log/antlrlab"));
		QueuedThreadPool threadPool = new QueuedThreadPool();
		threadPool.setMaxThreads(10);
		threadPool.setName("server");

		Server server = new Server(threadPool);

		ServerConnector http = new ServerConnector(server);
		http.setPort(80);

		server.addConnector(http);

//		Server server = new Server(8080);

		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.addServlet(new ServletHolder(new ParseServlet()), "/parse/*");
		context.addServlet(new ServletHolder(new ShareServlet()), "/share/*");
		context.addServlet(new ServletHolder(new LoadServlet()), "/load/*");

		ServletHolder holderHome = new ServletHolder("static-home", DefaultServlet.class);
		holderHome.setInitParameter("resourceBase", "static");
		holderHome.setInitParameter("dirAllowed","true");
		holderHome.setInitParameter("pathInfoOnly","true");
		context.addServlet(holderHome,"/*");

		server.setHandler(context);

		server.start();
	}
}
