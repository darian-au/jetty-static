package au.com.darian.jettystatic.app;

import java.net.InetAddress;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.InetAccessHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Extends the {@link InetAccessHandler} and filters on the Request Headers.
 * If the {@link #setHeaderItems()} are not set, then falls back to {@link InetAccessHandler} behavior.
 *
 * @author Darian Bridge.
 */
public class HeaderAccessHandler extends InetAccessHandler
{
    /** A logger. */
    private static final Logger log = Log.getLogger(HeaderAccessHandler.class);

    /** The header items. */
    private Map<String, List<String>> headerItems;

    /**
     * @param headerItems the header items to set.
     */
    public void setHeaderItems(final Map<String, List<String>> headerItems)
    {
        this.headerItems = headerItems;
    }

    /**
     * Checks if specified request headers are allowed by current rules.
     *
     * {@inheritDoc}
     */
    @Override
    protected boolean isAllowed(final InetAddress addr, final Request baseRequest, final HttpServletRequest request)
    {
        if (this.headerItems != null && !this.headerItems.isEmpty())
        {
            for (Enumeration<String> headerNames = baseRequest.getHeaderNames(); headerNames.hasMoreElements();)
            {
                String headerName = headerNames.nextElement();

                if (this.headerItems.containsKey(headerName))
                {
                    for (Enumeration<String> headers = baseRequest.getHeaders(headerName); headers.hasMoreElements();)
                    {
                        String header = headers.nextElement();

                        List<String> headerValues = this.headerItems.get(headerName);

                        if (headerValues != null && !headerValues.isEmpty())
                        {
                            for (String headerValue : headerValues)
                            {
                                if (header.contains(headerValue))
                                {
                                    return true;
                                }
                            }
                        }
                        else
                        {
                            log.warn("No values specified for header: " + headerName);
                        }
                    }
                }
            }

            return false;
        }
        else
        {
            return super.isAllowed(addr, baseRequest, request);
        }
    }
}
