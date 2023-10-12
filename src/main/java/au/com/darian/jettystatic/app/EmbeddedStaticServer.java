package au.com.darian.jettystatic.app;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Runs the Embedded Jetty HTTP Static File Server.
 *
 * @author Darian
 */
public class EmbeddedStaticServer
{
    /** A logger. */
    private final Logger log;

    /** The server. */
    private Server server;

    /** The http port. */
    private final int httpPort;

    /** The secure https port. */
    private final int securePort;

    /** The type of keystore file. */
    private final String keystoreType;

    /** The path to the keystore file. */
    private final String keystoreFile;

    /** The keystore password. */
    private final String keystorePassword;

    /** The static file directory. */
    private final File staticDir;

    /** A token used for shutdown. */
    private final String token;

    /**
     * Constructor.
     *
     * @param staticDir the static file directory.
     * @param httpPort the http port.
     * @param securePort the secure https port.
     * @param keystoreType the type of keystore file.
     * @param keystoreFile the path to the keystore file.
     * @param keystorePassword the keystore password.
     */
    public EmbeddedStaticServer(final File staticDir, final int httpPort, final int securePort,
            final String keystoreType, final String keystoreFile, final String keystorePassword)
    {
        this.log = Log.getLogger(this.getClass());

        this.staticDir = staticDir;

        this.httpPort = httpPort;
        this.securePort = securePort;

        this.keystoreType = keystoreType;
        this.keystoreFile = keystoreFile;
        this.keystorePassword = keystorePassword;

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
            System.out.println("usage: " + EmbeddedStaticServer.class.getSimpleName() + " (start | stop) [staticDir] [httpPort] [securePort] [keystoreType] [keystoreFile] [keystorePwd]");
            System.out.println("  staticDir:    the static directory to serve files from, default the current directory '.'");
            System.out.println("  httpPort:     the web server http port, default port 8080");
            System.out.println("  securePort:   the web server secure https port, default disabled (port 0)");
            System.out.println("  keystoreType: the type of jks keystore file");
            System.out.println("  keystoreFile: the path to the jks keystore file");
            System.out.println("  keystorePwd:  the jks keystore password");
            System.out.println();
            System.out.println("create keystore file:");
            System.out.println("  openssl pkcs12 -export -inkey privatekey.pem -in fullchain.pem -out keystore.pkcs12 -passout pass:pkcs12password -legacy");
            System.out.println("  keytool -importkeystore -noprompt -srckeystore keystore.pkcs12 -srcstoretype pkcs12 -srcstorepass pkcs12password -destkeystore keystore.jks -deststorepass jkspassword");
            System.out.println();

            return;
        }

        boolean shutdown = args.length < 1 ? false : "stop".equalsIgnoreCase(args[0]);
        File staticDir = getFile(args.length < 2 ? "." : args[1]);
        int httpPort = Integer.parseInt(args.length < 3 ? "8080" : args[2]);
        int securePort = Integer.parseInt(args.length < 4 ? "0" : args[3]);
        String keystoreType = args.length < 5 ? "PKCS12" : args[4];
        String keystoreFile = args.length < 6 ? "keystore.jks" : args[5];
        String keystorePassword = args.length < 7 ? "jkspassword" : args[6];

        EmbeddedStaticServer launcher = new EmbeddedStaticServer(staticDir, httpPort,
                securePort, keystoreType, keystoreFile, keystorePassword);

        if (shutdown)
        {
            launcher.stop();
        }
        else
        {
            launcher.log.info("Starting."
                + (httpPort > 0 ? (
                        " httpPort: [" + httpPort + "]") : "")
                + (securePort > 0 ? (
                        " securePort: [" + securePort + "]"
                                + " keystoreType: [" + keystoreType + "]"
                                + " keystoreFile: [" + keystoreFile + "]") : "")
                + " staticDir: [" + staticDir + "]");

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

        List<Connector> connectors = new ArrayList<Connector>();

        if (httpPort > 0)
        {
            HttpConfiguration config = new HttpConfiguration();

            if (securePort > 0)
            {
                config.setSecurePort(securePort);
            }

            ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(config));
            connector.setPort(httpPort);

            connectors.add(connector);
        }

        if (securePort > 0)
        {
            HttpConfiguration config = new HttpConfiguration();
            config.addCustomizer(new SecureRequestCustomizer());

            String password = Credential.getCredential(keystorePassword).toString();

            SslContextFactory context = new SslContextFactory.Server();
            context.setKeyStorePath(keystoreFile);
            context.setKeyStorePassword(password);
            context.setKeyManagerPassword(password);
            context.setKeyStoreType(keystoreType);

            ServerConnector connector = new ServerConnector(server,
                    new SslConnectionFactory(context, HttpVersion.HTTP_1_1.asString()),
                    new HttpConnectionFactory(config));
            connector.setPort(securePort);

            connectors.add(connector);
        }

        server.setConnectors(connectors.toArray(new Connector[connectors.size()]));

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
