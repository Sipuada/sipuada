package org.github.sipuada;

import android.javax.sip.RequestEvent;
import android.javax.sip.ServerTransaction;
import android.javax.sip.SipProvider;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.MessageFactory;
import android.javax.sip.message.Request;

public class UserAgentServer {

	private final SipProvider provider;
	private final MessageFactory messenger;
	private final HeaderFactory headerMaker;

	public UserAgentServer(SipProvider sipProvider, MessageFactory messageFactory, HeaderFactory headerFactory) {
		provider = sipProvider;
		messenger = messageFactory;
		headerMaker = headerFactory;
	}

	public void processRequest(RequestEvent requestEvent) {
		ServerTransaction serverTransaction = requestEvent.getServerTransaction();
		if (serverTransaction != null) {
			Request request = requestEvent.getRequest();
			String requestMethod = request.getMethod();
			handlerResquest(requestMethod, serverTransaction, request);
		} else {
			/*
			 * If the server transaction equals null the RequestEvent does not
			 * belong to an existing dialog and the application must determine
			 * how to handle the RequestEvent. If the application decides to
			 * forward the Request statelessly no transactional support is
			 * required and it can simply pass the Request of the RequestEvent
			 * as an argument to the {@link SipProvider#sendRequest(Request)}
			 * method. However if the application determines to respond to a
			 * Request statefully it must request a new server transaction from
			 * the {@link SipProvider#getNewServerTransaction(Request)}method
			 * and use this server transaction to send the Response based on the
			 * content of the Request.
			 */
		}

	}

	private void handlerResquest(String requestMethod, ServerTransaction serverTransaction, Request request) {
		switch (Constants.getRequestMethod(requestMethod)) {
		/*
		 * - The UAS application can accept the invitation by sending a 2xx
		 * response to the UAC, a 2xx response to an INVITE transaction
		 * establishes a session.
		 * 
		 * - If the UAS is not able to answer the invitation immediately, it can
		 * choose to indicate some kind of progress to the UAC (for example, an
		 * indication that a phone is ringing). This is accomplished with a
		 * provisional response between 101 and 199.
		 * 
		 * - If the invitation is not accepted, a 3xx, 4xx, 5xx or 6xx response
		 * is sent by the application, depending on the reason for the
		 * rejection.
		 */
		case INVITE:
			break;
		/*
		 * - A UAS core receiving a BYE request checks if it matches an existing
		 * dialog. If the BYE does not match an existing dialog, the UAS core
		 * SHOULD generate a 481 (Call/Transaction Does Not Exist) response and
		 * pass that to the server transaction.
		 * 
		 * - UAS SHOULD terminate the session (and therefore stop sending and
		 * listening for media) - Whether or not it ends its participation on
		 * the session, considering multicast sessions, the UAS core MUST
		 * generate a 2xx response to the BYE.
		 */
		case BYE:
			break;

		/*
		 * - If the UAS did not find a matching transaction for the CANCEL
		 * according to the procedure above, it SHOULD respond to the CANCEL
		 * with a 481 (Call Leg/Transaction Does Not Exist).
		 * 
		 * - If the UAS has not issued a final response for the original request,
		 * its behavior depends on the method of the original request. If the
		 * original request was an INVITE, the UAS SHOULD immediately respond to
		 * the INVITE with a 487 (Request Terminated).
		 * 
		 * - Regardless of the method of the original request, as long as the
		 * CANCEL matched an existing transaction, the UAS answers the CANCEL
		 * request itself with a 200 (OK) response(See Section 8.2.6).
		 */
		case CANCEL:
			break;
		default:
			break;
		}

	}

	public void sendResponse() {

	}

}
