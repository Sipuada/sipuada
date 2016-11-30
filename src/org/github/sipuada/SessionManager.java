package org.github.sipuada;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.github.sipuada.plugins.SipuadaPlugin.SessionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import android.javax.sdp.SdpFactoryImpl;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;
import android.javax.sip.header.ContentDispositionHeader;
import android.javax.sip.header.ContentTypeHeader;
import android.javax.sip.header.HeaderFactory;
import android.javax.sip.message.Message;
import android.javax.sip.message.Request;
import android.javax.sip.message.Response;

public class SessionManager {

	public static final EarlyMediaModel PRIORITARY_EARLY_MEDIA_MODEL = EarlyMediaModel.GATEWAY;

	public enum EarlyMediaModel {
		APPLICATION_SERVER, GATEWAY;
	}

	private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

	private final Map<RequestMethod, SipuadaPlugin> sessionPlugins;
	private final SipUserAgentRole role;
	private final String localAddress;
	private final HeaderFactory headerMaker;
	private final Map<String, Request> reqStore = new HashMap<>();
	private final Map<String, Response> provResStore = new HashMap<>();
	private final Map<String, Request> prackStore = new HashMap<>();
	private final Map<String, Response> prackResStore = new HashMap<>();
	private final Map<String, Response> resStore = new HashMap<>();
	private final Map<String, Request> ackStore = new HashMap<>();

	public enum SipUserAgentRole {
		UAC, UAS;
	}

	public SessionManager(Map<RequestMethod, SipuadaPlugin> sessionPlugins, EventBus bus,
		SipUserAgentRole role, String localAddress, HeaderFactory headerMaker) {
		this.sessionPlugins = sessionPlugins;
		this.role = role;
		this.localAddress = localAddress;
		this.headerMaker = headerMaker;
	}

	public boolean performOfferAnswerExchangeStep(String callId,
			Request request, Response provisionalResponse, Request prackRequest,
			Response prackResponse, Response finalResponse, Request ackRequest) {

		if (request == null) {
			request = reqStore.get(callId);
		}
		if (provisionalResponse == null) {
			provisionalResponse = provResStore.get(callId);
		}
		if (prackRequest == null) {
			prackRequest = prackStore.get(callId);
		}
		if (prackResponse == null) {
			prackResponse = prackResStore.get(callId);
		}
		if (finalResponse == null) {
			finalResponse = resStore.get(callId);
		}
		if (ackRequest == null) {
			ackRequest = ackStore.get(callId);
		}

		boolean output = doPerformOfferAnswerExchangeStep(callId,
			request, provisionalResponse, prackRequest,
			prackResponse, finalResponse, ackRequest);

		recordOfferAnswerExchangeMessages(callId,
			request, provisionalResponse, prackRequest,
			prackResponse, finalResponse, ackRequest);
		return output;
	}

	private boolean doPerformOfferAnswerExchangeStep(String callId,
			Request request, Response provisionalResponse, Request prackRequest,
			Response prackResponse, Response finalResponse, Request ackRequest) {

		logger.debug("$ Performing OFFER/ANSWER exchange step {}! $", callId);
		SipuadaPlugin sessionPlugin = sessionPlugins.get(RequestMethod.INVITE);

		logger.debug("$ Processing Req sdp - seeking Regular sdp... $");
		boolean reqHasRegularSdp = messageHasSdp
			(request, SessionType.REGULAR);
		logger.debug("$ Processing Req sdp - seeking Early sdp... $");
		boolean reqHasEarlySdp = messageHasSdp
			(request, SessionType.EARLY);
		boolean reqHasSdp = reqHasRegularSdp || reqHasEarlySdp;

		logger.debug("$ Processing ProvRes sdp - seeking Regular sdp... $");
		boolean provResHasRegularSdp = messageHasSdp
			(provisionalResponse, SessionType.REGULAR);
		logger.debug("$ Processing ProvRes sdp - seeking Early sdp... $");
		boolean provResHasEarlySdp = messageHasSdp
			(provisionalResponse, SessionType.EARLY);
		boolean provResHasSdp = provResHasRegularSdp || provResHasEarlySdp;

		logger.debug("$ Processing Prack sdp - seeking Regular sdp... $");
		boolean prackHasRegularSdp = messageHasSdp
			(prackRequest, SessionType.REGULAR);
		logger.debug("$ Processing Prack sdp - seeking Early sdp... $");
		boolean prackHasEarlySdp = messageHasSdp
			(prackRequest, SessionType.EARLY);
		boolean prackHasSdp = prackHasRegularSdp || prackHasEarlySdp;

		logger.debug("$ Processing PrackRes sdp - seeking Regular sdp... $");
		boolean prackResHasRegularSdp = messageHasSdp
			(prackResponse, SessionType.REGULAR);
		logger.debug("$ Processing PrackRes sdp - seeking Early sdp... $");
		boolean prackResHasEarlySdp = messageHasSdp
			(prackResponse, SessionType.EARLY);
		boolean prackResHasSdp = prackResHasRegularSdp || prackResHasEarlySdp;

		logger.debug("$ Processing Res sdp - seeking Regular sdp... $");
		boolean resHasRegularSdp = messageHasSdp
			(finalResponse, SessionType.REGULAR);
		logger.debug("$ Processing Res sdp - seeking Early sdp... $");
		boolean resHasEarlySdp = messageHasSdp
			(finalResponse, SessionType.EARLY);
		boolean resHasSdp = resHasRegularSdp || resHasEarlySdp;

		logger.debug("$ Processing Ack sdp - seeking Regular sdp... $");
		boolean ackHasRegularSdp = messageHasSdp
			(ackRequest, SessionType.REGULAR);
		logger.debug("$ Processing Ack sdp - seeking Early sdp... $");
		boolean ackHasEarlySdp = messageHasSdp
			(ackRequest, SessionType.EARLY);
		boolean ackHasSdp = ackHasRegularSdp || ackHasEarlySdp;

		logger.debug("$ Messages: Req: {{}}, ProvRes: {{}}, Prack: {{}}, "
			+ "PrackRes: {{}}, Res: {{}}, Ack: {{}}! $",
			reqHasSdp, provResHasSdp, prackHasSdp,
			prackResHasSdp, resHasSdp, ackHasSdp);

		if (!reqHasSdp && !provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && !resHasSdp && !ackHasSdp) {
			if (request != null) {
				boolean contentDispositionMatters = contentDispositionMatters(request);
				if (role == SipUserAgentRole.UAC) {
					return generateOffer(sessionPlugin, callId, SessionType.REGULAR,
						request, contentDispositionMatters);
//					CODE BELOW WAS REPLACED BY SNIPPET ABOVE
//					return generateOffer(sessionPlugin, callId, SessionType.REGULAR, request,
//						PRIORITARY_EARLY_MEDIA_MODEL != EarlyMediaModel.GATEWAY
//						? true : contentDispositionMatters);
				} else {
					if (finalResponse != null) {
						return generateOffer(sessionPlugin, callId, SessionType.REGULAR,
							finalResponse, contentDispositionMatters);
					} else if (provisionalResponse != null) {
						return generateOffer(sessionPlugin, callId, SessionType.EARLY,
							provisionalResponse, contentDispositionMatters);
//						CODE BELOW WAS REPLACED BY SNIPPET ABOVE
//						boolean regularGenerated = generateOffer(sessionPlugin, callId,
//							SessionType.REGULAR, provisionalResponse,
//							contentDispositionMatters);
//						if (PRIORITARY_EARLY_MEDIA_MODEL != EarlyMediaModel.GATEWAY) {
//							return regularGenerated && generateOffer(sessionPlugin, callId,
//								SessionType.EARLY, provisionalResponse,
//								contentDispositionMatters);
//						}
//						return regularGenerated;
					}
				}
			}
		} else if (reqHasSdp && !provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && !resHasSdp && !ackHasSdp) {
			boolean contentDispositionMatters = contentDispositionMatters(request);
			if (role == SipUserAgentRole.UAS) {
				if (finalResponse != null) {
					return generateAnswer(sessionPlugin, callId, SessionType.REGULAR,
						request, finalResponse, contentDispositionMatters);
				} else if (provisionalResponse != null) {
					return generateAnswer(sessionPlugin, callId, SessionType.EARLY,
						request, provisionalResponse, contentDispositionMatters);
//					CODE BELOW WAS REPLACED BY SNIPPET ABOVE
//					boolean regularGenerated = generateAnswer(sessionPlugin, callId,
//						SessionType.REGULAR, request, provisionalResponse,
//						contentDispositionMatters);
//					if (messageIsMultipart(request)) {
//						if (reqHasEarlySdp) {
//							return regularGenerated && generateAnswer(sessionPlugin,
//								callId, SessionType.EARLY, request,
//								provisionalResponse, contentDispositionMatters);
//						} else {
//							return regularGenerated && generateOffer(sessionPlugin,
//								callId, SessionType.EARLY, provisionalResponse,
//								contentDispositionMatters);
//						}
//					}
//					return regularGenerated;
				}
			}
		} else if (!reqHasSdp && provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && !resHasSdp && !ackHasSdp) {
			if (role == SipUserAgentRole.UAC) {
				boolean contentDispositionMatters = contentDispositionMatters
					(provisionalResponse);
				if (prackRequest != null) {
					return generateAnswer(sessionPlugin, callId, SessionType.EARLY,
						provisionalResponse, prackRequest, contentDispositionMatters);
//					CODE BELOW WAS REPLACED BY SNIPPET ABOVE
//					boolean regularGenerated = generateAnswer(sessionPlugin, callId,
//						SessionType.REGULAR, provisionalResponse, prackRequest,
//						contentDispositionMatters);
//					if (messageIsMultipart(provisionalResponse) && provResHasEarlySdp) {
//						return regularGenerated && generateAnswer(sessionPlugin, callId,
//							SessionType.EARLY, provisionalResponse, prackRequest,
//							contentDispositionMatters);
//					}
//					return regularGenerated;
				}
			}
		} else if (reqHasSdp && provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && !resHasSdp && !ackHasSdp) {
			if (role == SipUserAgentRole.UAS) {
				return true;
			} else {
				return receiveAnswerToAcceptedOffer(sessionPlugin, callId,
					SessionType.EARLY, provisionalResponse);
//				CODE BELOW WAS REPLACED BY SNIPPET ABOVE
//				boolean regularReceived = receiveAnswerToAcceptedOffer
//					(sessionPlugin, callId, SessionType.REGULAR, provisionalResponse);
//				if (prackRequest != null && provResHasEarlySdp) {
//					boolean contentDispositionMatters = contentDispositionMatters
//						(provisionalResponse);
//					if (messageIsMultipart(request)) {
//						if (reqHasEarlySdp) {
//							return regularReceived && receiveAnswerToAcceptedOffer
//								(sessionPlugin, callId, SessionType.EARLY,
//									provisionalResponse);
//						} else {
//							return regularReceived && generateAnswer(sessionPlugin,
//								callId, SessionType.EARLY, provisionalResponse,
//								prackRequest, contentDispositionMatters);
//						}
//					}
//				}
//				return regularReceived;
			}
		} else if (!reqHasSdp && provResHasSdp && prackHasSdp
				&& !prackResHasSdp && !resHasSdp && !ackHasSdp) {
			if (role == SipUserAgentRole.UAC) {
				return true;
			} else {
				return receiveAnswerToAcceptedOffer(sessionPlugin, callId,
					SessionType.EARLY, prackRequest);
//				CODE BELOW WAS REPLACED BY SNIPPET ABOVE
//				boolean regularReceived = receiveAnswerToAcceptedOffer
//					(sessionPlugin, callId, SessionType.REGULAR, prackRequest);
//				if (messageIsMultipart(provisionalResponse) && provResHasEarlySdp) {
//					return regularReceived && receiveAnswerToAcceptedOffer
//						(sessionPlugin, callId, SessionType.EARLY, prackRequest);
//				}
//				return regularReceived;
			}
		} else if (reqHasSdp && provResHasSdp && prackHasSdp
				&& !prackResHasSdp && !resHasSdp && !ackHasSdp) {
			if (role == SipUserAgentRole.UAS) {
				boolean contentDispositionMatters = contentDispositionMatters(prackRequest);
				if (reqHasEarlySdp) {
					return generateAnswer(sessionPlugin, callId, SessionType.EARLY,
						prackRequest, prackResponse, contentDispositionMatters);
				} else {
					return receiveAnswerToAcceptedOffer(sessionPlugin, callId,
						SessionType.EARLY, prackRequest);
				}
//				CODE BELOW WAS REPLACED BY SNIPPET ABOVE FIXME MAKE SURE IT WORKS, THOUGH
//				if (!reqHasEarlySdp && !provResHasEarlySdp && prackResponse != null) {
//					return generateAnswer(sessionPlugin, callId, SessionType.REGULAR,
//						prackRequest, prackResponse, contentDispositionMatters);
//				} else if (!reqHasEarlySdp && provResHasEarlySdp) {
//					boolean earlyReceived = receiveAnswerToAcceptedOffer(sessionPlugin,
//						callId, SessionType.EARLY, prackRequest);
//					if (prackHasRegularSdp && prackResponse != null) {
//						return earlyReceived && generateAnswer(sessionPlugin, callId,
//							SessionType.REGULAR, prackRequest, prackResponse,
//							contentDispositionMatters);
//					}
//				} else if (reqHasEarlySdp && provResHasEarlySdp && prackResponse != null) {
//					boolean regularGenerated = true, earlyGenerated = true;
//					if (prackHasRegularSdp) {
//						regularGenerated = generateAnswer(sessionPlugin, callId,
//							SessionType.REGULAR, prackRequest, prackResponse,
//							contentDispositionMatters);
//					}
//					if ((!prackHasRegularSdp || messageIsMultipart
//							(prackRequest)) && prackHasEarlySdp) {
//						earlyGenerated = generateAnswer(sessionPlugin, callId,
//							SessionType.EARLY, prackRequest, prackResponse,
//							contentDispositionMatters);
//					}
//					return regularGenerated && earlyGenerated;
//				}
			}
		} else if (reqHasSdp && provResHasSdp && prackHasSdp
				&& prackResHasSdp && !resHasSdp && !ackHasSdp) {
			if (role == SipUserAgentRole.UAC) {
				return receiveAnswerToAcceptedOffer(sessionPlugin, callId,
					SessionType.EARLY, prackResponse);
//				CODE BELOW WAS REPLACED BY SNIPPET ABOVE
//				boolean regularReceived = true, earlyReceived = true;
//				if (prackHasRegularSdp) {
//					regularReceived = receiveAnswerToAcceptedOffer(sessionPlugin, callId,
//						SessionType.REGULAR, prackResponse);
//				}
//				if ((!prackHasRegularSdp || messageIsMultipart
//						(prackRequest)) && prackHasEarlySdp) {
//					earlyReceived = receiveAnswerToAcceptedOffer(sessionPlugin, callId,
//						SessionType.EARLY, prackResponse);
//				}
//				return regularReceived && earlyReceived;
			}
		} else if (!reqHasSdp && !provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && resHasSdp && !ackHasSdp) {
			if (role == SipUserAgentRole.UAC) {
				boolean contentDispositionMatters = contentDispositionMatters(finalResponse);
				if (ackRequest != null) {
					return generateAnswer(sessionPlugin, callId,
						SessionType.REGULAR, finalResponse, ackRequest,
						contentDispositionMatters);
				}
			}
		} else if (reqHasSdp && !provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && resHasSdp && !ackHasSdp) {
			if (role == SipUserAgentRole.UAS) {
				return true;
			} else {
				return receiveAnswerToAcceptedOffer(sessionPlugin, callId,
					SessionType.REGULAR, finalResponse);
			}
		} else if (!reqHasSdp && !provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && resHasSdp && ackHasSdp) {
			if (role == SipUserAgentRole.UAC) {
				return true;
			} else {
				return receiveAnswerToAcceptedOffer(sessionPlugin, callId,
					SessionType.REGULAR, ackRequest);
			}
		} else if (reqHasSdp && !provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && resHasSdp && ackHasSdp) {
			if (role == SipUserAgentRole.UAS) {
				return true;
			}
		} else if (reqHasSdp && provResHasSdp && !prackHasSdp
				&& !prackResHasSdp && resHasSdp && !ackHasSdp) {
			return true;
		} else if (reqHasSdp && provResHasSdp && prackHasSdp
				&& !prackResHasSdp && resHasSdp && !ackHasSdp) {
			return true;
		} else if (reqHasSdp && provResHasSdp && prackHasSdp
				&& prackResHasSdp && resHasSdp && !ackHasSdp) {
			return true;
		}
		return false;
	}

	public void recordOfferAnswerExchangeMessages(String callId,
			Request request, Response provisionalResponse, Request prackRequest,
			Response prackResponse, Response finalResponse, Request ackRequest) {
		if (request != null) {
			reqStore.put(callId, request);
		}
		if (provisionalResponse != null) {
			provResStore.put(callId, provisionalResponse);
		}
		if (prackRequest != null) {
			prackStore.put(callId, prackRequest);
		}
		if (prackResponse != null) {
			prackResStore.put(callId, prackResponse);
		}
		if (finalResponse != null) {
			resStore.put(callId, finalResponse);
		}
		if (ackRequest != null) {
			ackStore.put(callId, ackRequest);
		}
	}

	public void wipeOfferAnswerExchangeMessages(String callId) {
		reqStore.put(callId, null);
		provResStore.put(callId, null);
		prackStore.put(callId, null);
		prackResStore.put(callId, null);
		resStore.put(callId, null);
		ackStore.put(callId, null);
	}

	private boolean contentDispositionMatters(Message message) {
		ContentTypeHeader contentTypeHeader = null;
		if (message != null) {
			contentTypeHeader = (ContentTypeHeader) message
				.getHeader(ContentTypeHeader.NAME);
		}
		if (contentTypeHeader != null && contentTypeHeader.getContentType()
				.toLowerCase().trim().equals("multipart")
				&& contentTypeHeader.getContentSubType()
				.toLowerCase().trim().equals("mixed")) {
			String boundary = contentTypeHeader.getParameter("boundary");
			String content = new String(message.getRawContent());
			String[] sdps = content.substring(boundary.length() + 3, content.length())
				.replace(boundary + "--", boundary).split("\\s*--" + boundary + "\\s*");
			for (String sdpWithHeaders : sdps) {
				boolean contentIsSession = false;
				boolean contentIsEarlySession = false;
				String[] headers = sdpWithHeaders.split("\\n\\n")[0].split("\\n");
				for (String header : headers) {
					header = header.trim();
					String headerKey = header.split(":")[0].trim().toLowerCase();
					String headerValue = header.split(":")[1].trim().toLowerCase();
					if (headerKey.equals("content-disposition")) {
						contentIsSession = headerValue.equals("session");
						contentIsEarlySession = headerValue.equals("early-session");
					}
				}
				return contentIsSession || contentIsEarlySession;
			}
		} else if (contentTypeHeader != null
			&& contentTypeHeader.getContentType()
				.toLowerCase().trim().equals("application")
			&& contentTypeHeader.getContentSubType()
				.toLowerCase().trim().equals("sdp")) {
			ContentDispositionHeader contentDispositionHeader
				= message.getContentDisposition();
			if (contentDispositionHeader != null) {
				String disposition = contentDispositionHeader
					.getDispositionType().trim().toLowerCase();
				return disposition.equals("session") && disposition.equals("early-session");
			}
		}
		return false;
	}

	private boolean messageHasSdp(Message message, SessionType type) {
		if (message == null) {
			logger.debug("So it is not of interest because it is null!");
			return false;
		}
		boolean messageHasContent = message != null && message.getContent() != null;
		logger.debug("$ Has content: {} $", messageHasContent);
		ContentTypeHeader contentTypeHeader = null;
		if (message != null) {
			contentTypeHeader = (ContentTypeHeader) message
				.getHeader(ContentTypeHeader.NAME);
		}
		if (contentTypeHeader != null && contentTypeHeader.getContentType()
				.toLowerCase().trim().equals("multipart")
				&& contentTypeHeader.getContentSubType()
				.toLowerCase().trim().equals("mixed")) {
			String boundary = contentTypeHeader.getParameter("boundary");
			logger.debug("$ And content contains multipart/mixed! (Boundary: {}) $", boundary);
			String content = new String(message.getRawContent());
			String[] sdps = content.substring(boundary.length() + 3, content.length())
				.replace(boundary + "--", boundary).split("\\s*--" + boundary + "\\s*");
			for (String sdpWithHeaders : sdps) {
				boolean contentIsSdp = false;
				boolean contentIsSession = false;
				boolean contentIsEarlySession = false;
				String[] headers = sdpWithHeaders.split("\\n\\n")[0].split("\\n");
				for (String header : headers) {
					header = header.trim();
					String headerKey = header.split(":")[0].trim().toLowerCase();
					String headerValue = header.split(":")[1].trim().toLowerCase();
					if (headerKey.equals("content-type")) {
						contentIsSdp = headerValue.equals("application/sdp");
					} else if (headerKey.equals("content-disposition")) {
						contentIsSession = headerValue.equals("session");
						contentIsEarlySession = headerValue.equals("early-session");
					}
				}
				if (contentIsSdp && (!contentIsSession && !contentIsEarlySession)) {
					contentIsSession = true;
				}
				if (contentIsSdp && ((type == SessionType.REGULAR && contentIsSession)
						|| (type == SessionType.EARLY && contentIsEarlySession))) {
					logger.debug("$ And some part is application/sdp of interest! $");
					return true;
				}
			}
			logger.debug("$ But no part is application/sdp of interest! $");
		} else if (contentTypeHeader != null
			&& contentTypeHeader.getContentType()
				.toLowerCase().trim().equals("application")
			&& contentTypeHeader.getContentSubType()
				.toLowerCase().trim().equals("sdp")) {
			logger.debug("$ And content contains application/sdp! $");
			ContentDispositionHeader contentDispositionHeader
				= message.getContentDisposition();
			if (contentDispositionHeader != null) {
				String disposition = contentDispositionHeader
					.getDispositionType().trim().toLowerCase();
				if (disposition.equals("session")) {
					logger.debug("$ So it is of interest since "
						+ "it has disposition: session! $");
					return true;
				} else if (disposition.equals("early-session")) {
					boolean isOfInterest = type == SessionType.EARLY;
					if (isOfInterest) {
						logger.debug("$ So it is of interest since it "
							+ "has disposition: early-session! $");
					} else {
						logger.debug("$ So it isn't of interest since it "
							+ "doesn't have disposition: early-session! $");
					}
					return isOfInterest;
				} else {
					boolean isOfInterest = disposition.equals(type.getDisposition());
					if (isOfInterest) {
						logger.debug("$ So it is of interest based on "
							+ "its disposition: {}! $", disposition);
					} else {
						logger.debug("$ So it is not of interest based on "
							+ "its disposition: {}! $", disposition);
					}
					return isOfInterest;
				}
			} else {
				logger.debug("$ And it is of interest since"
					+ " no disposition is specified! $");
				return true;
			}
		} else {
			logger.debug("$ And content doesn't contain application/sdp. $");
		}
		logger.debug("$ So this content is not of interest! $");
		return false;
	}

//	private boolean messageIsMultipart(Message message) {
//		ContentTypeHeader contentTypeHeader = null;
//		if (message != null) {
//			contentTypeHeader = (ContentTypeHeader) message
//				.getHeader(ContentTypeHeader.NAME);
//		}
//		if (contentTypeHeader != null && contentTypeHeader.getContentType()
//				.toLowerCase().trim().equals("multipart")
//				&& contentTypeHeader.getContentSubType()
//				.toLowerCase().trim().equals("mixed")) {
//			String boundary = contentTypeHeader.getParameter("boundary");
//			String content = new String(message.getRawContent());
//			String[] sdps = content.substring(boundary.length() + 3, content.length())
//				.replace(boundary + "--", boundary).split("\\s*--" + boundary + "\\s*");
//			int interestingSdpCount = 0;
//			for (String sdpWithHeaders : sdps) {
//				boolean contentIsSession = false;
//				boolean contentIsEarlySession = false;
//				String[] headers = sdpWithHeaders.split("\\n\\n")[0].split("\\n");
//				for (String header : headers) {
//					header = header.trim();
//					String headerKey = header.split(":")[0].trim().toLowerCase();
//					String headerValue = header.split(":")[1].trim().toLowerCase();
//					if (headerKey.equals("content-disposition")) {
//						contentIsSession = headerValue.equals("session");
//						contentIsEarlySession = headerValue.equals("early-session");
//					}
//				}
//				if (contentIsSession || contentIsEarlySession) {
//					interestingSdpCount++;
//				}
//			}
//			return interestingSdpCount > 1;
//		}
//		return false;
//	}

	public boolean messageHasSdp(Message message, boolean withContentDisposition) {
		boolean messageHasSdp = messageHasSdpOfInterest(message, false, null);
		return withContentDisposition == (message.getContentDisposition() != null)
			&& messageHasSdp;
	}

	public boolean messageHasSdpOfInterest(Message message,
			boolean dispositionMatters, String dispositionType) {
		boolean messageHasContent = message != null && message.getContent() != null;
		logger.debug("$ Has content: {} $", messageHasContent);
		ContentTypeHeader contentTypeHeader = null;
		if (message != null) {
			contentTypeHeader = (ContentTypeHeader) message
				.getHeader(ContentTypeHeader.NAME);
		}
		boolean contentIsSdp = contentTypeHeader != null
			&& contentTypeHeader.getContentType()
				.toLowerCase().trim().equals("application")
			&& contentTypeHeader.getContentSubType()
				.toLowerCase().trim().equals("sdp");
		logger.debug("$ And content is application/sdp: {} $", contentIsSdp);
		boolean messageHasSdpOfInterest = messageHasContent && contentIsSdp;
		if (dispositionMatters && dispositionType != null
				&& !dispositionType.trim().isEmpty()) {
			messageHasSdpOfInterest &= message != null
				&& (message.getContentDisposition() == null
				|| message.getContentDisposition().getDispositionType()
				.toLowerCase().trim().equals(dispositionType));
			logger.debug("$ And since disposition type {} matters, this "
				+ "SDP is interesting: {} $", dispositionType, messageHasSdpOfInterest);
		} else {
			logger.debug("$ And the disposition type doesn't matter. $");
		}
		return messageHasSdpOfInterest;
	}

	private boolean generateOffer(SipuadaPlugin sessionPlugin, String callId,
			SessionType type, Message offerMessage, boolean dispositionMatters) {
		String offerMessageIdentifier = offerMessage instanceof Request
			? ((Request) offerMessage).getMethod()
			: Integer.toString(((Response) offerMessage).getStatusCode());
		if (sessionPlugin == null) {
			logger.info("No plug-in available to generate offer "
				+ "to be inserted into {}.", offerMessageIdentifier);
			return true;
		}
		SessionDescription offer = null;
		try {
			offer = sessionPlugin.generateOffer(callId, type, localAddress);
			logger.debug("* {} just generated offer \n{}\n to be inserted into {} in "
				+ "context of call {}! *", role, offer, offerMessageIdentifier, callId);
		} catch (Throwable unexpectedException) {
			logger.error("Bad plug-in crashed while {} tried generating offer " +
				"to be inserted into {} in context of call {}.", role,
				offerMessageIdentifier, callId, unexpectedException);
			return false;
		}
		if (offer == null) {
			logger.error("{}'s plug-in generated no offer to be inserted into {}"
				+ " in context of call {}.",
				role, offerMessageIdentifier, callId);
			return true;
		}
		try {
			Object currentContent = offerMessage.getContent();
			ContentTypeHeader currentContentTypeHeader = (ContentTypeHeader) offerMessage
				.getHeader(ContentTypeHeader.NAME);
			if (currentContent == null || currentContent.toString().trim().isEmpty()) {
				offerMessage.setContent(offer, headerMaker
					.createContentTypeHeader("application", "sdp"));
				if (dispositionMatters) {
					offerMessage.setContentDisposition(headerMaker
						.createContentDispositionHeader(type.getDisposition()));
				}
			} else if (currentContentTypeHeader != null
					&& currentContentTypeHeader.getContentType().equals("multipart")
					&& currentContentTypeHeader.getContentSubType().equals("mixed")) {
				String boundary = currentContentTypeHeader.getParameter("boundary");
				StringBuilder content = new StringBuilder(currentContent.toString()
					.replace("--" + boundary + "--", "--" + boundary + "\n"));
				content.append("Content-Type: application/sdp\n");
				if (dispositionMatters) {
					content.append(String.format(Locale.US,
						"Content-Disposition: %s\n", type.getDisposition()));
				}
				content.append(String.format(Locale.US, "\n%s\n", offer.toString()));
				content.append(String.format(Locale.US, "--%s--", boundary));
				ContentTypeHeader multipartMixedContentTypeHeader = headerMaker
					.createContentTypeHeader("multipart", "mixed");
				multipartMixedContentTypeHeader.setParameter("boundary", boundary);
				offerMessage.setContent(content, multipartMixedContentTypeHeader);
				offerMessage.setContentDisposition(null);
			} else {
				StringBuilder content = new StringBuilder();
				String boundary = UUID.randomUUID().toString().substring(0, 8);
				content.append(String.format(Locale.US, "--%s\n", boundary));
				if (currentContentTypeHeader != null) {
					content.append(String.format(Locale.US, "Content-Type: %s/%s\n",
						currentContentTypeHeader.getContentType(),
						currentContentTypeHeader.getContentSubType()));
				}
				if (offerMessage.getContentDisposition() != null) {
					content.append(String.format(Locale.US, "Content-Disposition: %s\n",
						offerMessage.getContentDisposition().getDispositionType()));
				}
				content.append(String.format(Locale.US, "\n%s\n", currentContent));
				content.append(String.format(Locale.US, "--%s\n", boundary));
				content.append("Content-Type: application/sdp\n");
				if (dispositionMatters) {
					content.append(String.format(Locale.US,
						"Content-Disposition: %s\n", type.getDisposition()));
				}
				content.append(String.format(Locale.US, "\n%s\n", offer.toString()));
				content.append(String.format(Locale.US, "--%s--", boundary));
				ContentTypeHeader multipartMixedContentTypeHeader = headerMaker
						.createContentTypeHeader("multipart", "mixed");
				multipartMixedContentTypeHeader.setParameter("boundary", boundary);
				offerMessage.setContent(content, multipartMixedContentTypeHeader);
				offerMessage.setContentDisposition(null);
			}
			logger.info("{}'s plug-in-generated offer \n{}\n inserted into {}.",
				role, offer.toString(), offerMessageIdentifier);
			return true;
		} catch (ParseException parseException) {
			logger.error("{}'s plug-in-generated offer \n{}\n by could not "
				+ "be inserted into {}.", role, offer.toString(),
				offerMessageIdentifier, parseException);
			return false;
		}
	}

	private boolean generateAnswer(SipuadaPlugin sessionPlugin, String callId,
			SessionType type, Message offerMessage, Message answerMessage,
			boolean dispositionMatters) {
		String offerMessageIdentifier = offerMessage instanceof Request
			? ((Request) offerMessage).getMethod()
			: Integer.toString(((Response) offerMessage).getStatusCode());
		String answerMessageIdentifier = answerMessage instanceof Request
			? ((Request) answerMessage).getMethod()
			: Integer.toString(((Response) answerMessage).getStatusCode());
		if (sessionPlugin == null) {
			logger.info("No plug-in available to generate valid answer to offer in {},"
				+ " to be inserted into {}.", offerMessageIdentifier,
				answerMessageIdentifier);
			return false;
		}
		SessionDescription offer;
		try {
			offer = extractRelevantSdp(offerMessage, type);
		} catch (SdpParseException parseException) {
			logger.error("Offer arrived in {}, but could not be properly parsed,"
				+ " so it was discarded.", offerMessageIdentifier, parseException);
			return sessionPlugin == null;
		}
		SessionDescription answer = null;
		try {
			answer = sessionPlugin.generateAnswer(callId, type, offer, localAddress);
			logger.debug("* {}'s plug-in just generated answer \n{}\n to offer \n{}\n in {},"
				+ " to be inserted into {} in context of call {}! *", role, answer, offer,
				offerMessageIdentifier, answerMessageIdentifier, callId);
		} catch (Throwable unexpectedException) {
			logger.error("Bad plug-in crashed while {} tried generating answer to offer"
				+ " \n{}\n in {}, to be inserted into {}. The {} will terminate the dialog "
				+ "right away.", role, offer.toString(), offerMessageIdentifier,
				answerMessageIdentifier, role, unexpectedException);
			return false;
		}
		if (answer == null) {
			logger.error("{}'s plug-in could not generate valid answer to offer \n{}\n in {},"
				+ " to be inserted into {}.", role, offer.toString(),
				offerMessageIdentifier, answerMessageIdentifier);
			return false;
		}
		try {
			answerMessage.setContent(answer, headerMaker
				.createContentTypeHeader("application", "sdp"));
			if (dispositionMatters) {
				answerMessage.setContentDisposition(headerMaker
					.createContentDispositionHeader(type.getDisposition()));
			}
		} catch (ParseException parseException) {
			logger.error("{}'s plug-in-generated answer \n{}\n to offer \n{}\n in {} "
				+ "could not be inserted into {}.", role, answer.toString(), offer.toString(),
				offerMessageIdentifier, answerMessageIdentifier, parseException);
			return false;
		}
		logger.info("{}'s plug-in-generated answer \n{}\n to offer \n{}\n "
			+ "in {} was inserted into {}.", role, answer.toString(), offer.toString(),
			offerMessageIdentifier, answerMessageIdentifier);
		return true;
	}

	private boolean receiveAnswerToAcceptedOffer(SipuadaPlugin sessionPlugin,
			String callId, SessionType type, Message answerMessage) {
		String answerMessageIdentifier = answerMessage instanceof Request
			? ((Request) answerMessage).getMethod()
			: Integer.toString(((Response) answerMessage).getStatusCode());
		try {
			if (sessionPlugin == null) {
				logger.info("No plug-in available to process valid answer that"
					+ " has arrived within {}.", answerMessageIdentifier);
				return false;
			}
			SessionDescription answer = extractRelevantSdp(answerMessage, type);
			try {
				logger.debug("{}'s plug-in will process answer \n{}\n in context"
					+ " of call {}!", role, answer.toString(), callId);
				sessionPlugin.receiveAnswerToAcceptedOffer(callId, type, answer);
				return true;
			} catch (Throwable unexpectedException) {
				logger.error("Bad plug-in crashed while {} received answer "
					+ "that arrived alongside {}. The {} will terminate "
					+ "the dialog right away.", role, answerMessageIdentifier,
					role, unexpectedException);
			}
		} catch (SdpParseException parseException) {
			logger.error("Answer arrived in {}, but could not be properly parsed, so "
				+ "it was discarded. The {} will terminate the dialog right away.",
				answerMessageIdentifier, role, parseException);
		}
		return false;
	}

	private SessionDescription extractRelevantSdp(Message message,
			SessionType preferredType) throws SdpParseException {
		Object currentContent = message.getContent();
		ContentTypeHeader currentContentTypeHeader
			= (ContentTypeHeader) message.getHeader(ContentTypeHeader.NAME);
		if (currentContent == null || currentContent.toString().trim().isEmpty()) {
			return null;
		} else if (currentContentTypeHeader != null
				&& currentContentTypeHeader.getContentType().equals("multipart")
				&& currentContentTypeHeader.getContentSubType().equals("mixed")) {
			String boundary = currentContentTypeHeader.getParameter("boundary");
			String content = new String(message.getRawContent());
			String[] sdps = content.substring(boundary.length() + 3, content.length())
				.replace(boundary + "--", boundary).split("\\s*--" + boundary + "\\s*");
			String sessionSdp = null;
			for (String sdpWithHeaders : sdps) {
				String[] headers = sdpWithHeaders.split("\\n\\n")[0].split("\\n");
				String sdp = sdpWithHeaders.split("\\n\\n")[1];
				for (String header : headers) {
					header = header.trim();
					String headerKey = header.split(":")[0].trim().toLowerCase();
					String headerValue = header.split(":")[1].trim().toLowerCase();
					if (headerKey.equals("content-type")) {
						if (!headerValue.equals("application/sdp")) {
							continue;
						}
					} else if (headerKey.equals("content-disposition")) {
						if (headerValue.equals("session")) {
							sessionSdp = sdp;
						}
						if (headerValue.equals("session")
								&& preferredType == SessionType.REGULAR) {
							return SdpFactoryImpl.getInstance()
								.createSessionDescriptionFromString
								(new String(sessionSdp));
						} else if (headerValue.equals("early-session")
								&& preferredType == SessionType.EARLY) {
							return SdpFactoryImpl.getInstance()
								.createSessionDescriptionFromString
								(new String(sdp));
						}
					}
				}
			}
			return sessionSdp == null ? null : SdpFactoryImpl.getInstance()
				.createSessionDescriptionFromString(new String(sessionSdp));
		} else {
			return SdpFactoryImpl.getInstance().createSessionDescriptionFromString
				(new String(message.getRawContent()));
		}
	}

	public boolean isSessionOngoing(String callId, SessionType type) {
		RequestMethod requestMethod = RequestMethod.INVITE;
		SipuadaPlugin sessionPlugin = sessionPlugins.get(requestMethod);
		return sessionPlugin.isSessionOngoing(callId, type);
	}

	public static boolean performSessionSetup(SipuadaPlugin sessionPlugin,
			String callId, SessionType sessionType, SipUserAgent sipUserAgent) {
		if (sessionPlugin == null) {
			return true;
		}
		boolean veredict = true;
		for (SessionType type : SessionType.values()) {
			if (sessionPlugin.isSessionOngoing(callId, type)) {
				veredict &= performSessionTermination(sessionPlugin, callId, type);
			}
		}
		return veredict &= sessionPlugin.performSessionSetup
			(callId, sessionType, sipUserAgent);
//		if (!veredict && sessionType == SessionType.EARLY) {
//			veredict &= sessionPlugin.performSessionSetup
//				(callId, SessionType.REGULAR, sipUserAgent);
//		}
//		return veredict;
	}

	public static boolean performSessionTermination(SipuadaPlugin sessionPlugin,
			String callId, SessionType sessionType) {
		if (sessionPlugin == null) {
			return true;
		}
		return sessionPlugin.performSessionTermination(callId, sessionType);
	}

}
