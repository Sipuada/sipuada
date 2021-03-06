/*
 * Conditions Of Use
 *
 * This software was developed by employees of the National Institute of
 * Standards and Technology (NIST), an agency of the Federal Government.
 * Pursuant to title 15 Untied States Code Section 105, works of NIST
 * employees are not subject to copyright protection in the United States
 * and are considered to be in the public domain.  As a result, a formal
 * license is not needed to use the software.
 *
 * This software is provided by NIST as a service and is expressly
 * provided "AS IS."  NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED
 * OR STATUTORY, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
 * AND DATA ACCURACY.  NIST does not warrant or make any representations
 * regarding the use of the software or the results thereof, including but
 * not limited to the correctness, accuracy, reliability or usefulness of
 * the software.
 *
 * Permission to use this software is contingent upon your acceptance
 * of the terms of this agreement
 *
 * .
 *
 */
package android.gov.nist.javax.sip.stack;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.text.ParseException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.gov.nist.core.InternalErrorHandler;
import android.gov.nist.javax.sip.IOExceptionEventExt;
import android.gov.nist.javax.sip.IOExceptionEventExt.Reason;
import android.gov.nist.javax.sip.SipListenerExt;
import android.gov.nist.javax.sip.SipProviderImpl;
import android.gov.nist.javax.sip.SipStackImpl;
import android.gov.nist.javax.sip.header.RetryAfter;
import android.gov.nist.javax.sip.header.Via;
import android.gov.nist.javax.sip.header.ViaList;
import android.gov.nist.javax.sip.message.SIPMessage;
import android.gov.nist.javax.sip.message.SIPRequest;
import android.gov.nist.javax.sip.message.SIPResponse;
import android.gov.nist.javax.sip.parser.Pipeline;
import android.gov.nist.javax.sip.parser.PipelinedMsgParser;
import android.gov.nist.javax.sip.parser.SIPMessageListener;
import android.javax.sip.ListeningPoint;
import android.javax.sip.SipListener;
import android.javax.sip.address.Hop;
import android.javax.sip.message.Response;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public abstract class ConnectionOrientedMessageChannel extends MessageChannel implements
	SIPMessageListener, Runnable, RawMessageChannel {
	
	private static Logger logger = LoggerFactory.getLogger(ConnectionOrientedMessageChannel.class);
	protected SIPTransactionStack sipStack;
	
	protected Socket mySock;
	
	protected PipelinedMsgParser myParser;
	
	protected String key;
	
	protected InputStream myClientInputStream; // just to pass to thread.

	// Set here on initialization to avoid thread leak. See issue 266
	protected boolean isRunning = true;
	
	protected boolean isCached;

    protected Thread mythread;    

    protected String myAddress;

    protected int myPort;

    protected InetAddress peerAddress;
    
    // This is the port and adress that we will find in the headers of the messages from the peer
    protected int peerPortAdvertisedInHeaders = -1;
    protected String peerAddressAdvertisedInHeaders;
    
    protected int peerPort;

    protected String peerProtocol;
	
	private volatile long lastKeepAliveReceivedTime;

    private SIPStackTimerTask pingKeepAliveTimeoutTask;
    private Semaphore keepAliveSemaphore;
    
    private long keepAliveTimeout;    
    
    public ConnectionOrientedMessageChannel(SIPTransactionStack sipStack) {
    	this.sipStack = sipStack;
    	this.keepAliveTimeout = sipStack.getReliableConnectionKeepAliveTimeout();
    	if(keepAliveTimeout > 0) {
    		keepAliveSemaphore = new Semaphore(1);
    	}
	}
    
    /**
     * Returns "true" as this is a reliable transport.
     */
    public boolean isReliable() {
        return true;
    }
    
    /**
     * Close the message channel.
     */
    public void close() {
    	close(true, true);
    }
    
    protected abstract void close(boolean removeSocket, boolean stopKeepAliveTask);
    
	/**
     * Get my SIP Stack.
     *
     * @return The SIP Stack for this message channel.
     */
    public SIPTransactionStack getSIPStack() {
        return sipStack;
    }
    
    /**
     * get the address of the client that sent the data to us.
     *
     * @return Address of the client that sent us data that resulted in this
     *         channel being created.
     */
    public String getPeerAddress() {
        if (peerAddress != null) {
            return peerAddress.getHostAddress();
        } else
            return getHost();
    }

    protected InetAddress getPeerInetAddress() {
        return peerAddress;
    }

    public String getPeerProtocol() {
        return this.peerProtocol;
    }
    
    /**
     * Return a formatted message to the client. We try to re-connect with the
     * peer on the other end if possible.
     *
     * @param sipMessage
     *            Message to send.
     * @throws IOException
     *             If there is an error sending the message
     */
    public void sendMessage(final SIPMessage sipMessage) throws IOException {

        if (!sipMessage.isNullRequest() ) {
            logger.debug("sendMessage:: " + sipMessage.getFirstLine() + " cseq method = " + sipMessage.getCSeq().getMethod());
        }

        for (MessageProcessor messageProcessor : getSIPStack()
                .getMessageProcessors()) {
            if (messageProcessor.getIpAddress().getHostAddress().equals(
                    this.getPeerAddress())
                    && messageProcessor.getPort() == this.getPeerPort()
                    && messageProcessor.getTransport().equalsIgnoreCase(
                            this.getPeerProtocol())) {
                Runnable processMessageTask = new Runnable() {

                    public void run() {
                        try {
                            processMessage((SIPMessage) sipMessage.clone());
                        } catch (Exception ex) {
                            logger.error("Error self routing message cause by: ", ex);
                        }
                    }
                };
                getSIPStack().getSelfRoutingThreadpoolExecutor().execute(
                        processMessageTask);
                logger.debug(
                    "Self routing message");
                return;
            }

        }

        byte[] msg = sipMessage.encodeAsBytes(this.getTransport());

        long time = System.currentTimeMillis();
        
        // need to store the peerPortAdvertisedInHeaders in case the response has an rport (ephemeral) that failed to retry on the regular via port
        // for responses, no need to store anything for subsequent requests.
        if(peerPortAdvertisedInHeaders <= 0) {
        	if(sipMessage instanceof SIPResponse) {
        		SIPResponse sipResponse = (SIPResponse) sipMessage; 
        		Via via = sipResponse.getTopmostVia();
        		if(via.getRPort() > 0) {
	            	if(via.getPort() <=0) {    
	            		// if port is 0 we assume the default port for TCP
	            		this.peerPortAdvertisedInHeaders = 5060;
	            	} else {
	            		this.peerPortAdvertisedInHeaders = via.getPort();
	            	}
                	logger.debug("1.Storing peerPortAdvertisedInHeaders = " + peerPortAdvertisedInHeaders + " for via port = " + via.getPort() + " via rport = " + via.getRPort() + " and peer port = " + peerPort + " for this channel " + this + " key " + key);
        		}
        	}
        }

        // JvB: also retry for responses, if the connection is gone we should
        // try to reconnect
        this.sendMessage(msg, sipMessage instanceof SIPRequest);

        // message was sent without any exception so let's set set port and
        // address before we feed it to the logger
        sipMessage.setRemoteAddress(this.peerAddress);
        sipMessage.setRemotePort(this.peerPort);
        sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());
        sipMessage.setLocalPort(this.getPort());

        logMessage(sipMessage, peerAddress, peerPort, time);
    }

	protected abstract void sendMessage(byte[] msg, boolean b) throws IOException;
	
	public void processMessage(SIPMessage sipMessage, InetAddress address) {
        this.peerAddress = address;
        try {
            processMessage(sipMessage);
        } catch (Exception e) {
            logger.error(
        		"ERROR processing self routing", e);
        }
    }
	
	 /**
     * Gets invoked by the parser as a callback on successful message parsing
     * (i.e. no parser errors).
     *
     * @param sipMessage
     *            Message to process (this calls the application for processing
     *            the message).
     *
     *            Jvb: note that this code is identical to TCPMessageChannel,
     *            refactor some day
     */
    public void processMessage(SIPMessage sipMessage) throws Exception {
        try {
        	if (sipMessage.getFrom() == null || sipMessage.getTo() == null
                    || sipMessage.getCallId() == null
                    || sipMessage.getCSeq() == null
                    || sipMessage.getViaHeaders() == null) {
                String badmsg = sipMessage.encode();
                logger.error("bad message " + badmsg);
                logger.error(">>> Dropped Bad Msg");
                return;
            }
        	
            sipMessage.setRemoteAddress(this.peerAddress);
            sipMessage.setRemotePort(this.getPeerPort());
            sipMessage.setLocalAddress(this.getMessageProcessor().getIpAddress());
            sipMessage.setLocalPort(this.getPort());
            //Issue 3: https://telestax.atlassian.net/browse/JSIP-3
            sipMessage.setPeerPacketSourceAddress(this.peerAddress);
            sipMessage.setPeerPacketSourcePort(this.peerPort);
            
            ViaList viaList = sipMessage.getViaHeaders();
            // For a request
            // first via header tells where the message is coming from.
            // For response, this has already been recorded in the outgoing
            // message.
            if (sipMessage instanceof SIPRequest) {
                Via v = (Via) viaList.getFirst();
                // the peer address and tag it appropriately.
                Hop hop = sipStack.addressResolver.resolveAddress(v.getHop());
                this.peerProtocol = v.getTransport();
                //if(peerPortAdvertisedInHeaders <= 0) {
                	int hopPort = v.getPort();
                	logger.debug("hop port = " + hopPort + " for request " + sipMessage + " for this channel " + this + " key " + key);
                	if(hopPort <= 0) {    
                		// if port is 0 we assume the default port for TCP
                		this.peerPortAdvertisedInHeaders = 5060;
                	} else {
                		this.peerPortAdvertisedInHeaders = hopPort;
                	}
                	logger.debug("3.Storing peerPortAdvertisedInHeaders = " + peerPortAdvertisedInHeaders + " for this channel " + this + " key " + key);
                //}
                // may be needed to reconnect, when diff than peer address
                if(peerAddressAdvertisedInHeaders == null) {
                	peerAddressAdvertisedInHeaders = hop.getHost();
                	logger.debug("3.Storing peerAddressAdvertisedInHeaders = " + peerAddressAdvertisedInHeaders + " for this channel " + this + " key " + key);
                }
                
                try {
                	if (mySock != null) { // selfrouting makes socket = null
                        				 // https://jain-sip.dev.java.net/issues/show_bug.cgi?id=297
                		this.peerAddress = mySock.getInetAddress();
                	}
                    // Check to see if the received parameter matches
                	// the peer address and tag it appropriately.
                	
                    // JvB: dont do this. It is both costly and incorrect
                    // Must set received also when it is a FQDN, regardless
                    // whether
                    // it resolves to the correct IP address
                    // InetAddress sentByAddress =
                    // InetAddress.getByName(hop.getHost());
                    // JvB: if sender added 'rport', must always set received
                	boolean hasRPort = v.hasParameter(Via.RPORT);
                    if(!hasRPort && v.getPort() != peerPort) {
                    	// https://github.com/RestComm/jain-sip/issues/79
                		logger.debug(
                            "setting rport since viaPort " + v.getPort() + " different than peerPacketSourcePort "
                            + peerPort + " so that the response can be routed back");
                    	hasRPort = true;
                    }
                    if (hasRPort
                            || !hop.getHost().equals(
                                    this.peerAddress.getHostAddress())) {
                        v.setParameter(Via.RECEIVED, this.peerAddress
                                .getHostAddress());
                    }
                    // @@@ hagai
                    // JvB: technically, may only do this when Via already
                    // contains
                    // rport
                    v.setParameter(Via.RPORT, Integer.toString(this.peerPort));
                } catch (java.text.ParseException ex) {
                    InternalErrorHandler.handleException(ex);
                }
                // Use this for outgoing messages as well.
                if (!this.isCached && mySock != null) { // self routing makes
									                    // mySock=null
									                    // https://jain-sip.dev.java.net/issues/show_bug.cgi?id=297
                	this.isCached = true;
                    int remotePort = ((java.net.InetSocketAddress) mySock
                            .getRemoteSocketAddress()).getPort();
                    String key = IOHandler.makeKey(mySock.getInetAddress(),
                            remotePort);
                    if(this.messageProcessor instanceof NioTcpMessageProcessor) {
                    	// https://java.net/jira/browse/JSIP-475 don't use iohandler in case of NIO communications of the socket will leak in the iohandler sockettable
                    	((NioTcpMessageProcessor)this.messageProcessor).nioHandler.putSocket(key, mySock.getChannel());
                    } else {
                    	sipStack.ioHandler.putSocket(key, mySock);
                    }
                    // since it can close the socket it needs to be after the mySock usage otherwise
                    // it the socket will be disconnected and NPE will be thrown in some edge cases
                    ((ConnectionOrientedMessageProcessor)this.messageProcessor).cacheMessageChannel(this);
                }
            }

            // Foreach part of the request header, fetch it and process it

            long receptionTime = System.currentTimeMillis();
            //

            if (sipMessage instanceof SIPRequest) {
                // This is a request - process the request.
                SIPRequest sipRequest = (SIPRequest) sipMessage;
                // Create a new sever side request processor for this
                // message and let it handle the rest.

                logger.debug(
                    "----Processing Message---");
                sipStack.serverLogger.logMessage(sipMessage, this
                        .getPeerHostPort().toString(),
                        this.messageProcessor.getIpAddress()
                                .getHostAddress()
                                + ":" + this.messageProcessor.getPort(),
                        false, receptionTime);
                // Check for reasonable size - reject message
                // if it is too long.
                if (sipStack.getMaxMessageSize() > 0
                        && sipRequest.getSize()
                                + (sipRequest.getContentLength() == null ? 0
                                        : sipRequest.getContentLength()
                                                .getContentLength()) > sipStack
                                .getMaxMessageSize()) {
                    SIPResponse sipResponse = sipRequest
                            .createResponse(SIPResponse.MESSAGE_TOO_LARGE);
                    byte[] resp = sipResponse
                            .encodeAsBytes(this.getTransport());
                    this.sendMessage(resp, false);
                    throw new Exception("Message size exceeded");
                }

                String sipVersion = ((SIPRequest) sipMessage).getRequestLine()
                        .getSipVersion();
                if (!sipVersion.equals("SIP/2.0")) {
                    SIPResponse versionNotSupported = ((SIPRequest) sipMessage)
                            .createResponse(Response.VERSION_NOT_SUPPORTED,
                                    "Bad SIP version " + sipVersion);
                    this.sendMessage(versionNotSupported.encodeAsBytes(this
                            .getTransport()), false);
                    throw new Exception("Bad version ");
                }

                String method = ((SIPRequest) sipMessage).getMethod();
                String cseqMethod = ((SIPRequest) sipMessage).getCSeqHeader()
                        .getMethod();

                if (!method.equalsIgnoreCase(cseqMethod)) {
                    SIPResponse sipResponse = sipRequest
                    .createResponse(SIPResponse.BAD_REQUEST);
                    byte[] resp = sipResponse
                            .encodeAsBytes(this.getTransport());
                    this.sendMessage(resp, false);
                    throw new Exception("Bad CSeq method" + sipMessage + " method " + method);
                }

                // Stack could not create a new server request interface.
                // maybe not enough resources.
                ServerRequestInterface sipServerRequest = sipStack
                        .newSIPServerRequest(sipRequest, this);
                
                if (sipServerRequest != null) {
                    try {
                        sipServerRequest.processRequest(sipRequest, this);
                    } finally {
                        if (sipServerRequest instanceof SIPTransaction) {
                            SIPServerTransaction sipServerTx = (SIPServerTransaction) sipServerRequest;
                            if (!sipServerTx.passToListener())
                                ((SIPTransaction) sipServerRequest)
                                        .releaseSem();
                        }
                    }
                } else {
                	if(sipStack.sipMessageValve == null) { // Allow message valves to nullify messages without error
                		SIPResponse response = sipRequest
                				.createResponse(Response.SERVICE_UNAVAILABLE);

                		RetryAfter retryAfter = new RetryAfter();

                		// Be a good citizen and send a decent response code back.
                		try {
                			retryAfter.setRetryAfter((int) (10 * (Math.random())));
                			response.setHeader(retryAfter);
                			this.sendMessage(response);
                		} catch (Exception e) {
                			// IGNore
                		}
            			logger.warn(
        					"Dropping message -- could not acquire semaphore");
                	}
                }
            } else {
            	SIPResponse sipResponse = (SIPResponse) sipMessage;
                // JvB: dont do this
                // if (sipResponse.getStatusCode() == 100)
                // sipResponse.getTo().removeParameter("tag");
                try {
                    sipResponse.checkHeaders();
                } catch (ParseException ex) {
                    logger.error(
                        "Dropping Badly formatted response message >>> "
                                + sipResponse);
                    return;
                }
                // This is a response message - process it.
                // Check the size of the response.
                // If it is too large dump it silently.
                if (sipStack.getMaxMessageSize() > 0
                        && sipResponse.getSize()
                                + (sipResponse.getContentLength() == null ? 0
                                        : sipResponse.getContentLength()
                                                .getContentLength()) > sipStack
                                .getMaxMessageSize()) {
                    logger.debug(
                        "Message size exceeded");
                    return;

                }

                ServerResponseInterface sipServerResponse = sipStack
                        .newSIPServerResponse(sipResponse, this);
                if (sipServerResponse != null) {
                    try {
                        if (sipServerResponse instanceof SIPClientTransaction
                                && !((SIPClientTransaction) sipServerResponse)
                                        .checkFromTag(sipResponse)) {
                            logger.error(
                                    "Dropping response message with invalid tag >>> "
                                            + sipResponse);
                            return;
                        }

                        sipServerResponse.processResponse(sipResponse, this);
                    } finally {
                        if (sipServerResponse instanceof SIPTransaction
                                && !((SIPTransaction) sipServerResponse)
                                        .passToListener()) {
                            // Note that the semaphore is released in event
                            // scanner if the
                            // request is actually processed by the Listener.
                            ((SIPTransaction) sipServerResponse).releaseSem();
                        }
                    }
                } else {
                	logger
                    	.warn(
                            "Application is blocked -- could not acquire semaphore -- dropping response");
                }
            }
        } finally {
        }
    }
    
    
  

    /**
     * This gets invoked when thread.start is called from the constructor.
     * Implements a message loop - reading the tcp connection and processing
     * messages until we are done or the other end has closed.
     */
    public void run() {
        Pipeline hispipe = null;
        // Create a pipeline to connect to our message parser.
        hispipe = new Pipeline(myClientInputStream, sipStack.readTimeout,
                ((SIPTransactionStack) sipStack).getTimer());
        // Create a pipelined message parser to read and parse
        // messages that we write out to him.
        myParser = new PipelinedMsgParser(sipStack, this, hispipe,
                this.sipStack.getMaxMessageSize());
        // Start running the parser thread.
        myParser.processInput();
        // bug fix by Emmanuel Proulx
        int bufferSize = 4096;
        ((ConnectionOrientedMessageProcessor)this.messageProcessor).useCount++;
        this.isRunning = true;
        try {
            while (true) {
                try {
                    byte[] msg = new byte[bufferSize];
                    int nbytes = myClientInputStream.read(msg, 0, bufferSize);
                    // no more bytes to read...
                    if (nbytes == -1) {
                        hispipe.write("\r\n\r\n".getBytes("UTF-8"));
                        try {
                            if (sipStack.maxConnections != -1) {
                                synchronized (messageProcessor) {
                                	((ConnectionOrientedMessageProcessor)this.messageProcessor).nConnections--;
                                	messageProcessor.notify();
                                }
                            }
                            hispipe.close();
                            close();
                        } catch (IOException ioex) {
                        }
                        return;
                    }                    
                    
                    hispipe.write(msg, 0, nbytes);

                } catch (IOException ex) {
                    // Terminate the message.
                    try {
                        hispipe.write("\r\n\r\n".getBytes("UTF-8"));
                    } catch (Exception e) {
                        // InternalErrorHandler.handleException(e);
                    }

                    try {
                        logger.debug(
                            "IOException closing sock " + ex);
                        try {
                            if (sipStack.maxConnections != -1) {
                                synchronized (messageProcessor) {
                                	((ConnectionOrientedMessageProcessor)this.messageProcessor).nConnections--;
                                	messageProcessor.notify();
                                }
                            }
                            close();
                            hispipe.close();
                        } catch (IOException ioex) {
                        }
                    } catch (Exception ex1) {
                        // Do nothing.
                    }
                    return;
                } catch (Exception ex) {
                    InternalErrorHandler.handleException(ex, logger);
                }
            }
        } finally {
            this.isRunning = false;
            ((ConnectionOrientedMessageProcessor)this.messageProcessor).remove(this);
            ((ConnectionOrientedMessageProcessor)this.messageProcessor).useCount--;
            // parser could be null if the socket was closed by the remote end already
            if(myParser != null) {
            	myParser.close();
            }
        }

    }

    
    protected void uncache() {
        if (isCached && !isRunning) {
        	((ConnectionOrientedMessageProcessor)this.messageProcessor).remove(this);
        }
    }
    
    /**
     * Get an identifying key. This key is used to cache the connection and
     * re-use it if necessary.
     */
    public String getKey() {
        if (this.key != null) {
            return this.key;
        } else {
            this.key = MessageChannel.getKey(this.peerAddress, this.peerPort,
                    getTransport());
            return this.key;
        }
    }

    /**
     * Get the host to assign to outgoing messages.
     *
     * @return the host to assign to the via header.
     */
    public String getViaHost() {
        return myAddress;
    }

    /**
     * Get the port for outgoing messages sent from the channel.
     *
     * @return the port to assign to the via header.
     */
    public int getViaPort() {
        return myPort;
    }

    /**
     * Get the port of the peer to whom we are sending messages.
     *
     * @return the peer port.
     */
    public int getPeerPort() {
        return peerPort;
    }

    public int getPeerPacketSourcePort() {
        return this.peerPort;
    }

    public InetAddress getPeerPacketSourceAddress() {
        return this.peerAddress;
    }
    
	/*
     * (non-Javadoc)
     * @see gov.nist.javax.sip.parser.SIPMessageListener#sendSingleCLRF()
     */
	public void sendSingleCLRF() throws Exception {
        lastKeepAliveReceivedTime = System.currentTimeMillis();

		if(mySock != null && !mySock.isClosed()) {
			sendMessage("\r\n".getBytes("UTF-8"), false);
		}

        synchronized (this) {
            if (isRunning) {
            	if(keepAliveTimeout > 0) {
            		rescheduleKeepAliveTimeout(keepAliveTimeout);
            	}             
            }
        }
    }

    public void cancelPingKeepAliveTimeoutTaskIfStarted() {
    	if(pingKeepAliveTimeoutTask != null && pingKeepAliveTimeoutTask.getSipTimerTask() != null) {
    		try {
				keepAliveSemaphore.acquire();
			} catch (InterruptedException e) {
				logger.error("Couldn't acquire keepAliveSemaphore");
				return;
			}
	    	try {
                logger.debug("~~~ cancelPingKeepAliveTimeoutTaskIfStarted for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
                        +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + ")");
	    		sipStack.getTimer().cancel(pingKeepAliveTimeoutTask);
	    	} finally {
	    		keepAliveSemaphore.release();
	    	}
    	}
    }

    public void setKeepAliveTimeout(long keepAliveTimeout) {
        if (keepAliveTimeout < 0){
            cancelPingKeepAliveTimeoutTaskIfStarted();
        }
        if (keepAliveTimeout == 0){
            keepAliveTimeout = messageProcessor.getSIPStack().getReliableConnectionKeepAliveTimeout();
        }

        logger.debug("~~~ setKeepAliveTimeout for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
                +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + ")");

        this.keepAliveTimeout = keepAliveTimeout;
        if(keepAliveSemaphore == null) {
        	keepAliveSemaphore = new Semaphore(1);
        }

        boolean isKeepAliveTimeoutTaskScheduled = pingKeepAliveTimeoutTask != null;
        if (isKeepAliveTimeoutTaskScheduled && keepAliveTimeout > 0){
            rescheduleKeepAliveTimeout(keepAliveTimeout);
        }
    }
    
    public long getKeepAliveTimeout() {
    	return keepAliveTimeout;
    }

    public void rescheduleKeepAliveTimeout(long newKeepAliveTimeout) {
//        long now = System.currentTimeMillis();
//        long lastKeepAliveReceivedTimeOrNow = lastKeepAliveReceivedTime == 0 ? now : lastKeepAliveReceivedTime;
//
//        long newScheduledTime =  lastKeepAliveReceivedTimeOrNow + newKeepAliveTimeout;

        StringBuilder methodLog = new StringBuilder();

        methodLog.append("~~~ rescheduleKeepAliveTimeout for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
                +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + "): newKeepAliveTimeout=");
        if (newKeepAliveTimeout == Long.MAX_VALUE) {
            methodLog.append("Long.MAX_VALUE");
        } else {
            methodLog.append(newKeepAliveTimeout);
        }
//            methodLog.append(", lastKeepAliveReceivedTimeOrNow=");
//            methodLog.append(lastKeepAliveReceivedTimeOrNow);
//            methodLog.append(", newScheduledTime=");
//            methodLog.append(newScheduledTime);

//        long delay = newScheduledTime > now ? newScheduledTime - now : 1;
        try {
			keepAliveSemaphore.acquire();
		} catch (InterruptedException e) {
			logger.warn("Couldn't acquire keepAliveSemaphore");
			return;
		}
		try{
	        if(pingKeepAliveTimeoutTask == null) {
	        	pingKeepAliveTimeoutTask = new KeepAliveTimeoutTimerTask();	  	        	
                methodLog.append(", scheduling pingKeepAliveTimeoutTask to execute after ");
                methodLog.append(keepAliveTimeout / 1000);
                methodLog.append(" seconds");
                logger.debug(methodLog.toString());
	    	    sipStack.getTimer().schedule(pingKeepAliveTimeoutTask, keepAliveTimeout);
	        } else {
                logger.debug("~~~ cancelPingKeepAliveTimeout for MessageChannel(key=" + key + "), clientAddress=" + peerAddress
                    +  ", clientPort=" + peerPort+ ", timeout="+ keepAliveTimeout + ")");
        		sipStack.getTimer().cancel(pingKeepAliveTimeoutTask);        	
        		pingKeepAliveTimeoutTask = new KeepAliveTimeoutTimerTask();
                methodLog.append(", scheduling pingKeepAliveTimeoutTask to execute after ");
                methodLog.append(keepAliveTimeout / 1000);
                methodLog.append(" seconds");
                logger.debug(methodLog.toString());
        		sipStack.getTimer().schedule(pingKeepAliveTimeoutTask, keepAliveTimeout);
	        }
		} finally {
        	keepAliveSemaphore.release();
        }
    }
    class KeepAliveTimeoutTimerTask extends SIPStackTimerTask {

        public void runTask() {
            logger.debug(
                "~~~ Starting processing of KeepAliveTimeoutEvent( " + peerAddress.getHostAddress() + "," + peerPort + ")...");
            close(true, true);
            if(sipStack instanceof SipStackImpl) {
	            for (Iterator<SipProviderImpl> it = ((SipStackImpl)sipStack).getSipProviders(); it.hasNext();) {
	                SipProviderImpl nextProvider = (SipProviderImpl) it.next();
	                SipListener sipListener= nextProvider.getSipListener();
	                ListeningPoint[] listeningPoints = nextProvider.getListeningPoints();
	                for(ListeningPoint listeningPoint : listeningPoints) {
		            	if(sipListener!= null && sipListener instanceof SipListenerExt
		            			// making sure that we don't notify each listening point but only the one on which the timeout happened  
		            			&& listeningPoint.getIPAddress().equalsIgnoreCase(myAddress) && listeningPoint.getPort() == myPort && 
		            				listeningPoint.getTransport().equalsIgnoreCase(getTransport())) {
		            		((SipListenerExt)sipListener).processIOException(new IOExceptionEventExt(nextProvider, Reason.KeepAliveTimeout, myAddress, myPort,
		            				peerAddress.getHostAddress(), peerPort, getTransport()));
		                }
	                }
	            }  
            } else {
	            SipListener sipListener = sipStack.getSipListener();	            
	            if(sipListener instanceof SipListenerExt) {
	            	((SipListenerExt)sipListener).processIOException(new IOExceptionEventExt(this, Reason.KeepAliveTimeout, myAddress, myPort,
	                    peerAddress.getHostAddress(), peerPort, getTransport()));
	            }
            }
        }
    }
}
