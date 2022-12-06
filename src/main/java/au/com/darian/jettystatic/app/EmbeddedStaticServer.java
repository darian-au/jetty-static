package au.com.darian.jettystatic.app;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
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
        File staticDir = new File(args.length < 3 ? "." : args[2]);

        EmbeddedStaticServer launcher = new EmbeddedStaticServer(httpPort, staticDir);

        if (shutdown)
        {
            launcher.stop();
        }
        else
        {
            launcher.create();
            launcher.start();
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
            URL url = new URL("http://localhost:" + httpPort + "/shutdown");

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            int responseCode = connection.getResponseCode();

            log.info("Server stopped. " + responseCode + " : " + connection.getResponseMessage());
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

        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        server.setConnectors(new Connector[] {http});

        HandlerCollection handlers = new HandlerList();

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

        ResourceHandler resourceHandler = new ResourceHandler();
        PathResource pathResource = new PathResource(staticDir);

        resourceHandler.setDirectoriesListed(false);
        resourceHandler.setBaseResource(pathResource);
        handlers.addHandler(resourceHandler);

        handlers.addHandler(new ShutdownHandler(server));

        server.setHandler(handlers);
    }
}
