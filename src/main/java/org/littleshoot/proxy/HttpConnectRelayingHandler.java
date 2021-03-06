package org.littleshoot.proxy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that simply relays traffic the channel this is connected to to 
 * another channel passed in to the constructor.
 */
@ChannelPipelineCoverage("one")
public class HttpConnectRelayingHandler extends SimpleChannelUpstreamHandler {
    
    private final Logger m_log = 
        LoggerFactory.getLogger(HttpRelayingHandler.class);
    
    /**
     * The channel to relay to. This could be a connection from the browser
     * to the proxy or it could be a connection from the proxy to an external
     * site.
     */
    private final Channel m_relayChannel;

    private final ChannelGroup m_channelGroup;

    /**
     * Creates a new {@link HttpConnectRelayingHandler} with the specified 
     * connection to relay to..
     * 
     * @param relayChannel The channel to relay messages to.
     * @param channelGroup The group of channels to close on shutdown.
     */
    public HttpConnectRelayingHandler(final Channel relayChannel, 
        final ChannelGroup channelGroup) {
        this.m_relayChannel = relayChannel;
        this.m_channelGroup = channelGroup;
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, 
        final MessageEvent e) throws Exception {
        final ChannelBuffer msg = (ChannelBuffer) e.getMessage();
        if (m_relayChannel.isOpen()) {
            final ChannelFutureListener logListener = new ChannelFutureListener() {
                public void operationComplete(final ChannelFuture future) 
                    throws Exception {
                    m_log.info("Finished writing data");
                }
            };
            m_relayChannel.write(msg).addListener(logListener);
        }
        else {
            m_log.info("Channel not open. Connected? {}", 
                m_relayChannel.isConnected());
            // This will undoubtedly happen anyway, but just in case.
            if (e.getChannel().isOpen()) {
                e.getChannel().close();
            }
        }
    }
    
    @Override
    public void channelOpen(final ChannelHandlerContext ctx, 
        final ChannelStateEvent cse) throws Exception {
        final Channel ch = cse.getChannel();
        m_log.info("New channel opened from proxy to web: {}", ch);
        this.m_channelGroup.add(ch);
    }

    @Override
    public void channelClosed(final ChannelHandlerContext ctx, 
        final ChannelStateEvent e) throws Exception {
        m_log.info("Got closed event on proxy -> web connection: "+e.getChannel());
        //closeOnFlush(m_browserToProxyChannel);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, 
        final ExceptionEvent e) throws Exception {
        m_log.warn("Caught exception on proxy -> web connection: "+
            e.getChannel(), e.getCause());
        if (e.getChannel().isOpen()) {
            closeOnFlush(e.getChannel());
        }
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    private void closeOnFlush(final Channel ch) {
        m_log.info("Closing channel on flush: {}", ch);
        if (ch.isConnected()) {
            ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(
                ChannelFutureListener.CLOSE);
        }
    }
}
