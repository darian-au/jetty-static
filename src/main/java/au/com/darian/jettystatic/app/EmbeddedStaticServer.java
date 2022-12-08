package au.com.darian.jettystatic.app;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.PathResource;

/**
 * Runs the Embedded Jetty HTTP Static File Server.
 *
 * @author Darian Bridge.
 */
public class EmbeddedStaticServer
{
    /** A logger. */
    private final Logger log;

    /** The server. */
    private Server server;

    /** The http port. */
    private int httpPort;

    /** The static file directory. */
    private File staticDir;

    private String token;

    /**
     * Constructor.
     *
     * @param httpPort the http port.
     * @param staticDir the static file directory.
     */
    public EmbeddedStaticServer(final int httpPort, final File staticDir)
    {
        this.log = Log.getLogger(this.getClass());

        this.httpPort = httpPort;
        this.staticDir = staticDir;

        this.token = System.getProperty("app.token", "679e6682dfbd7b22eeffef6bd02ac7b2");
    }

    /**
     * Runs the Embedded Jetty HTTP Static File Server.
     *
     * @param args command line arguments.
     * @throws Exception thrown if a problem occurs.
     */
    public static void main(final String[] args) throws Exception
    {
        if (args.length < 1)
        {
            System.out.println("usage: " + EmbeddedStaticServer.class.getSimpleName() + " (start | stop) [httpPort] [staticDir]");
            System.out.println("  httpPort:   the web server port, default port 8080");
            System.out.println("  staticDir:  the static directory to serve files from, default the current directory '.'");

            return;
        }

        boolean shutdown = args.length < 1 ? false : "stop".equalsIgnoreCase(args[0]);
        int httpPort = Integer.parseInt(args.length < 2 ? "8080" : args[1]);
        File staticDir = getFile(args.length < 3 ? "." : args[2]);

        EmbeddedStaticServer launcher = new EmbeddedStaticServer(httpPort, staticDir);

        if (shutdown)
        {
            launcher.stop();
        }
        else
        {
            launcher.log.info("Starting. httpPort: [" + httpPort + "] staticDir: " + staticDir);

            launcher.create();
            launcher.start();
        }
    }

    /**
     * Expands a path with user home location information.
     *
     * @param path the path to expand.
     * @return the expanded canonical path.
     * @throws IOException thrown if there was a problem.
     */
    public static File getFile(final String path) throws IOException
    {
        if (path.startsWith("~" + File.separator))
        {
            return new File(System.getProperty("user.home") + path.substring(1)).getCanonicalFile();
        }
        else if (path.startsWith("~"))
        {
            throw new UnsupportedOperationException("Home dir expansion not implemented for explicit usernames.");
        }
        else
        {
            return new File(path).getCanonicalFile();
        }
    }

    /**
     * Start the server.
     *
     * @throws Exception thrown if a problem occurs.
     */
    public void start() throws Exception
    {
        server.start();
        server.join();
    }

    /**
     * Stop the server. Establishes a HTTP connection to the server to send a POST request with the shutdown command.
     *
     * @throws IOException thrown if a problem occurs.
     */
    public void stop() throws IOException
    {
        try
        {
            URL url = new URL("http://localhost:" + httpPort + "/shutdown?token=" + this.token);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            int responseCode = connection.getResponseCode();

            log.info("Server stopped. " + responseCode + " : " + connection.getResponseMessage());
        }
        catch (SocketException ex)
        {
            log.info("Server not running. " + ex.getClass().getName() + ": " + ex.getMessage());
        }
        catch (IOException ex)
        {
            log.warn("Failed to stop server. " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    /**
     * Create the server and establish the handler chain.
     */
    public void create()
    {
        server = new Server();

        HttpConfiguration httpConfig = new HttpConfiguration();

        ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        http.setPort(httpPort);


        server.setConnectors(new Connector[] {http});

        // Set up the handler chain.
        HandlerCollection handlers = new HandlerList();

        // Log handler.
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        CustomRequestLog requestLog = new CustomRequestLog(new RequestLog.Writer()
        {
            private final Logger log = Log.getLogger(this.getClass());

            @Override
            public void write(final String requestEntry) throws IOException
            {
                log.info(requestEntry);
            }
        }, CustomRequestLog.EXTENDED_NCSA_FORMAT);
        requestLogHandler.setRequestLog(requestLog);
        handlers.addHandler(requestLogHandler);

        // Resource handler.
        ResourceHandler resourceHandler = new ResourceHandler();
        PathResource pathResource = new PathResource(staticDir);

        resourceHandler.setDirectoriesListed(false);
        resourceHandler.setBaseResource(pathResource);

        // Access handler.
        HeaderAccessHandler accessHandler = new HeaderAccessHandler();
        Map<String, List<String>> headerItems = new HashMap<String, List<String>>();
        headerItems.put(HttpHeader.USER_AGENT.toString(), Arrays.asList(
            // Shutdown handler.
            System.getProperty("app.agent.java", "Java"),
            // Let's Encrypt validation server.
            System.getProperty("app.agent.letsencrypt", "Let's Encrypt validation server")
        ));
        accessHandler.setHeaderItems(headerItems);

        // Wrap the resource handler with the access handler.
        accessHandler.setHandler(resourceHandler);
        handlers.addHandler(accessHandler);

        // Shutdown handler.
        handlers.addHandler(new ShutdownHandler(this.token, false, false));

        // Set the handler chain.
        server.setHandler(handlers);

        // Error handler.
        server.setErrorHandler(new ErrorHandler()
        {
            /** {@inheritDoc} */
            @Override
            protected void handleErrorPage(final HttpServletRequest request, final Writer writer, final int code, final String message)
            {
                // Don't provide any response page for errors.
            }
        });
    }
}
