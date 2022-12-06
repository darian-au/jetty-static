package au.com.darian.jettystatic.app;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Shutdown handler for the embedded static server.
 * Handles a POST request with a special URL as a shutdown command.
 *
 * @author Darian Bridge.
 */
public class ShutdownHandler extends AbstractHandler
{
    /** A logger. */
    private static final Logger log = Log.getLogger(ShutdownHandler.class);

    /** The server. */
    private final Server server;

    /**
     * Constructor.
     *
     * @param server The server.
     */
    public ShutdownHandler(final Server server)
    {
        this.server = server;
    }

    /**
     * Handle the request. Looks for the shutdown command in a POST request.
     */
    @Override
    public void handle(final String target, final Request baseRequest,
            final HttpServletRequest request, final HttpServletResponse response)
            throws IOException, ServletException
    {
        if (!target.equals("/shutdown"))
        {
            return;
        }

        if (!request.getMethod().equals("POST"))
        {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        log.info("Handling server shutdown request.");
        baseRequest.setHandled(true);

        new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    server.stop();
                }
                catch (InterruptedException ex)
                {
                    log.debug("Ignoring interruption.", ex);
                }
                catch (Exception ex)
                {
                    throw new RuntimeException("Problem shutting down server.", ex);
                }
            }
        }.start();
    }
}
