package org.github.sipuada.plugins;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;

import org.github.sipuada.SipUserAgent;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.CandidateType;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.CandidateHarvester;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.sdp.CandidateAttribute;
import org.ice4j.ice.sdp.IceSdpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.gov.nist.javax.sdp.MediaDescriptionImpl;
import android.gov.nist.javax.sdp.fields.AttributeField;
import android.gov.nist.javax.sdp.fields.MediaField;
import android.gov.nist.javax.sdp.fields.OriginField;
import android.gov.nist.javax.sdp.fields.SDPKeywords;
import android.gov.nist.javax.sdp.fields.SessionNameField;
import android.javax.sdp.Connection;
import android.javax.sdp.Media;
import android.javax.sdp.MediaDescription;
import android.javax.sdp.SdpConstants;
import android.javax.sdp.SdpException;
import android.javax.sdp.SdpFactoryImpl;
import android.javax.sdp.SdpParseException;
import android.javax.sdp.SessionDescription;

public abstract class SipuadaPlugin {

	protected final Map<String, Record> records = new HashMap<>();
    protected final Map<String, CallRole> roles = new HashMap<>();
    protected final Set<MediaCodecInstance> audioCodecs = new HashSet<>();
    protected final Set<MediaCodecInstance> videoCodecs = new HashSet<>();
    protected final Map<String, Map<MediaCodecInstance, Session>> preparedStreams = new HashMap<>();
    protected final Map<String, SipUserAgent> postponedStreams = new HashMap<>();
    protected final Map<String, Boolean> startedStreams = new HashMap<>();

    protected final Map<String, Agent> iceAgents = new HashMap<>();
    protected CandidateHarvester stunHarvester = null;
    protected CandidateHarvester turnHarvester = null;

    protected Logger logger;
    protected String pluginClass;
    protected String identifier;
    protected boolean iceIsLocallySupported;

    public void startPlugin(String identifier, String pluginClass,
    		Class<? extends SupportedMediaCodec> mediaCodecsEnumClas) {
    	startPlugin(identifier, pluginClass, true, mediaCodecsEnumClas);
    }

	public void startPlugin(String identifier, String pluginClass, boolean iceIsSupported,
			Class<? extends SupportedMediaCodec> mediaCodecsEnumClass) {
		this.pluginClass = pluginClass;
		this.identifier = identifier;
		this.iceIsLocallySupported = iceIsSupported;
		this.logger = LoggerFactory.getLogger(pluginClass);
		logger.info("{} sipuada plugin for {} instantiated.",
			pluginClass, identifier);
		for (SupportedMediaCodec mediaCodec : parseMediaCodecs(mediaCodecsEnumClass)) {
			MediaCodecInstance enabledMediaCodec = new MediaCodecInstance(mediaCodec);
			switch (mediaCodec.getMediaType()) {
				case AUDIO:
					audioCodecs.add(enabledMediaCodec);
					break;
				case VIDEO:
					videoCodecs.add(enabledMediaCodec);
					break;
				default:
					break;
			}
		}
		doStartPlugin();
	}

	protected abstract void doStartPlugin();

	public void stopPlugin() {
		doStopPlugin();
	}

	protected abstract void doStopPlugin();

	public enum SessionType {

		REGULAR("session"),
		EARLY("early-session");

		private String disposition;

		SessionType(String disposition) {
			this.disposition = disposition;
		}

		public String getDisposition() {
			return disposition;
		}

	}

	protected enum SupportedMediaType {

        AUDIO("audio"),
        MESSAGE("message"),
        VIDEO("video"),
        DATA("data");

    	private final String mediaTypeName;

    	private SupportedMediaType(String mediaTypeName) {
            this.mediaTypeName = mediaTypeName;
        }

    	@Override
        public String toString() {
            return mediaTypeName;
        }

    	public static SupportedMediaType parseString(String mediaTypeName)
            throws IllegalArgumentException {
            if (AUDIO.toString().equals(mediaTypeName)) {
            	return AUDIO;
            } else if (MESSAGE.toString().equals(mediaTypeName)) {
            	return MESSAGE;
            } else if (VIDEO.toString().equals(mediaTypeName)) {
            	return VIDEO;
            } else if (DATA.toString().equals(mediaTypeName)) {
            	return DATA;
            }
            throw new IllegalArgumentException
        		(mediaTypeName + " is not a currently supported MediaType");
        }

    }

	public interface SupportedMediaCodec {

		public String getEncoding();

    	public int getType();

    	public int getClockRate();

    	public SupportedMediaType getMediaType();

    	public boolean isEnabled();

	}

	public class MediaCodecInstance {

    	private final String encoding;
    	private final int type;
    	private final int clockRate;
    	private final SupportedMediaType mediaType;
    	private boolean isEnabled;

    	private MediaCodecInstance(SupportedMediaCodec mediaCodec) {
        	this.encoding = mediaCodec.getEncoding();
    		this.type = mediaCodec.getType();
    		this.clockRate = mediaCodec.getClockRate();
    		this.mediaType = mediaCodec.getMediaType();
    		this.isEnabled = mediaCodec.isEnabled();
    	}

    	public String getEncoding() {
    		return encoding;
    	}

    	public int getType() {
			return type;
		}

    	public int getClockRate() {
			return clockRate;
		}

    	public String getRtpmap() {
			return String.format(Locale.US, "%s/%d", encoding, clockRate);
		}

    	public SupportedMediaType getMediaType() {
			return mediaType;
		}

    	public boolean isEnabled() {
    		return isEnabled;
    	}

    	public void setEnabled(boolean isEnabled) {
    		this.isEnabled = isEnabled;
    	}

	}

	private Set<SupportedMediaCodec> parseMediaCodecs
			(Class<? extends SupportedMediaCodec> mediaCodecsEnumClass) {
		Object[] enumConstants = mediaCodecsEnumClass.getEnumConstants();
		Set<SupportedMediaCodec> mediaCodecs = new HashSet<>();
		for (Object enumConstant : enumConstants) {
			final SupportedMediaCodec availableMediaCodec
				= (SupportedMediaCodec) enumConstant;
			mediaCodecs.add(new SupportedMediaCodec() {

				@Override
				public String getEncoding() {
					return availableMediaCodec.getEncoding();
				}

				@Override
				public int getType() {
					return availableMediaCodec.getType();
				}

				@Override
				public int getClockRate() {
					return availableMediaCodec.getClockRate();
				}

				@Override
				public SupportedMediaType getMediaType() {
					return availableMediaCodec.getMediaType();
				}

				@Override
				public boolean isEnabled() {
					return availableMediaCodec.isEnabled();
				}

			});
		}
		return mediaCodecs;
	}

	protected enum CallRole {
        CALLEE, CALLER
    }

	protected class Record {

		Map<String, SessionDescription> storage = new HashMap<>();

		public Record(SessionDescription offer) {
			storage.put("offer", offer);
		}

		public Record(SessionDescription offer, SessionDescription answer) {
			storage.put("offer", offer);
			storage.put("answer", answer);
		}

		public SessionDescription getOffer() {
			return storage.get("offer");
		}

		public void setOffer(SessionDescription offer) {
			storage.put("offer", offer);
		}

		public SessionDescription getAnswer() {
			return storage.get("answer");
		}

		public void setAnswer(SessionDescription answer) {
			storage.put("answer", answer);
		}

	}

	protected enum MediaDirection {
		SENDRECV, SENDONLY, RECVONLY, INACTIVE
	}

	protected class Session {

    	private final String localDataAddress;
    	private final int localDataPort;
    	private final String localControlAddress;
    	private final int localControlPort;
    	private final String remoteDataAddress;
    	private final int remoteDataPort;
    	private final String remoteControlAddress;
    	private final int remoteControlPort;
    	private MediaDirection direction;
    	private Object payload;

    	public Session(String localDataAddress, int localDataPort,
    			String localControlAddress, int localControlPort,
    			String remoteDataAddress, int remoteDataPort,
    			String remoteControlAddress, int remoteControlPort,
    			MediaDirection direction) {
			super();
			this.localDataAddress = localDataAddress;
			this.localDataPort = localDataPort;
			this.localControlAddress = localControlAddress;
			this.localControlPort = localControlPort;
			this.remoteDataAddress = remoteDataAddress;
			this.remoteDataPort = remoteDataPort;
			this.remoteControlAddress = remoteControlAddress;
			this.remoteControlPort = remoteControlPort;
			this.direction = direction;
		}

    	public String getLocalDataAddress() {
			return localDataAddress;
		}

    	public int getLocalDataPort() {
			return localDataPort;
		}

    	public String getLocalControlAddress() {
			return localControlAddress;
		}

    	public int getLocalControlPort() {
			return localControlPort;
		}

    	public String getRemoteDataAddress() {
			return remoteDataAddress;
		}

    	public int getRemoteDataPort() {
			return remoteDataPort;
		}

    	public String getRemoteControlAddress() {
			return remoteControlAddress;
		}

    	public int getRemoteControlPort() {
			return remoteControlPort;
		}

    	public MediaDirection getDirection() {
			return direction;
		}

    	public void setDirection(MediaDirection direction) {
			this.direction = direction;
		}

    	public Object getPayload() {
			return payload;
		}

    	public void setPayload(Object payload) {
			this.payload = payload;
		}

    }

	/**
	 * Generates offer to go along a session-creating request.
	 * @return a SessionDescription representing an offer or null if the plug-in
	 * wishes to propose no offer to a request of given method.
	 */
	public SessionDescription generateOffer(String callId, SessionType type, String localAddress) {
		logger.debug("===*** generateOffer -> {}", getSessionKey(callId, type));
		roles.put(getSessionKey(callId, type),  CallRole.CALLER);
		try {
			Agent iceAgent = createOrFetchExistingAgent
				(getSessionKey(callId, type), localAddress);
			SessionDescription offer = createSdpOffer(localAddress);
			records.put(getSessionKey(callId, type), new Record(offer));
			logger.info("{} generating {} offer {{}} in context of call invitation {}...",
				pluginClass, type, offer, callId);
			try {
				return includeOfferedMediaTypes(offer, iceAgent);
			} catch (Throwable anyIssue) {
    			logger.error("{} could not include supported media types into "
					+ "offer {{}} in context of call invitation {}...",
					pluginClass, offer, callId, anyIssue);
    			return null;
			}
		} catch (Throwable anyIssue) {
			logger.error("{} could not generate offer in context of call invitation {}...",
				pluginClass, callId, anyIssue);
			return null;
		}
	}

	/**
	 * Feeds the accepted answer to a given offer back to the plug-in that generated
	 * that offer. The plug-in should know which offer this answer corresponds to
	 * by comparing the given callId with the one that was passed to generateOffer().
	 * This method is important because the plug-in may need to use both
	 * the original offer and the accepted answer to perform the session setup stage.
	 * If this instance receives a generateOffer(), it MUST expect an upcoming
	 * receiveAcceptedAnswer() in the future (and then, a performSessionSetup()).
	 * If this instance receives a generateAnswer(), it MUST NOT expect an upcoming
	 * receiveAcceptedAnswer() because obviously this was the plug-in that accepted
	 * someone else's offer, and thus it must only expect a performSessionSetup() later.
	 */
	public void receiveAnswerToAcceptedOffer(String callId, SessionType type,
			SessionDescription answer) {
		logger.debug("===*** receiveAnswerToAcceptedOffer -> {}", getSessionKey(callId, type));
		Record record = records.get(getSessionKey(callId, type));
		Agent iceAgent = iceAgents.get(getSessionKey(callId, type));
		SessionDescription offer = record.getOffer();
		record.setAnswer(answer);
		logger.info("{} received {} answer {{}} to {} offer {{}} in context of call "
			+ "invitation {}...", pluginClass, type, answer, type, offer, callId);
		try {
			prepareForSessionSetup(callId, type, offer, answer, iceAgent);
		} catch (Throwable anyIssue) {
			logger.error("{} could not prepare for {} session setup in "
				+ "context of call invitation {}!", pluginClass, type, callId, anyIssue);
		}
	}

	/**
	 * Generates an answer to an offer to go along a response to a session-creating request.
	 * @return a SessionDescription representing the answer to an offer or null if the
	 * plug-in could not elaborate a valid answer to it.
	 */
	public SessionDescription generateAnswer(String callId, SessionType type,
			SessionDescription offer, String localAddress) {
		logger.debug("===*** generateAnswer -> {}", getSessionKey(callId, type));
        roles.put(getSessionKey(callId, type), CallRole.CALLEE);
        try {
			Agent iceAgent = createOrFetchExistingAgent
				(getSessionKey(callId, type), localAddress);
    		SessionDescription answer = createSdpAnswer(offer, localAddress);
    		records.put(getSessionKey(callId, type), new Record(offer, answer));
    		logger.info("{} generating {} answer {{}} to {} offer {{}} in context "
    			+ "of call invitation {}...", pluginClass, type, answer, type, offer, callId);
    		try {
        		return includeAcceptedMediaTypes(callId, type, answer, offer, iceAgent);
    		} catch (Throwable anyIssue) {
    			logger.error("{} could not include accepted media types "
					+ "into {} answer {{}} to {} offer {{}} in context of call invitation"
					+ " {}...", pluginClass, type, answer, type, offer, callId, anyIssue);
    			return null;
    		}
        } catch (Throwable anyIssue) {
			logger.error("{} could not generate {} answer to {} offer {{}} in context of "
				+ "call invitation {}...", pluginClass, type, type, offer, callId, anyIssue);
			return null;
        }
	}

	private Agent createOrFetchExistingAgent(String sessionKey, String localAddress) {
		Agent iceAgent = iceAgents.get(sessionKey);
		if (iceAgent == null) {
			iceAgent = new Agent(Level.ALL, localAddress);
			iceAgent.setTrickling(false);
			iceAgent.setUseHostHarvester(true);
			turnHarvester = createTurnHarvester();
			if (turnHarvester != null) {
				iceAgent.addCandidateHarvester(turnHarvester);
			}
			stunHarvester = createStunHarvester();
			if (stunHarvester != null) {
				iceAgent.addCandidateHarvester(stunHarvester);
			}
			iceAgents.put(sessionKey, iceAgent);
		}
		return iceAgent;
	}

	private CandidateHarvester createTurnHarvester() {
		return null;
	}

	private CandidateHarvester createStunHarvester() {
    	try {
			return new StunCandidateHarvester(new TransportAddress
				(InetAddress.getByName("stun.icchw2.jflddns.com.br"), 19302, Transport.UDP));
		} catch (UnknownHostException stunServerUnavailable) {
			stunServerUnavailable.printStackTrace();
	    	return null;
		}
	}

	private SessionDescription createSdpOffer(String localAddress)
			throws SdpException {
		return createSdp(localAddress, System.currentTimeMillis() / 1000, 0L, "-");
	}

	private SessionDescription createSdpAnswer(SessionDescription offer,
			String localAddress) throws SdpException {
		return createSdp(localAddress, offer.getOrigin().getSessionId(),
			offer.getOrigin().getSessionVersion(), offer.getSessionName().getValue());
	}

	private SessionDescription createSdp(String localAddress, long sessionId,
			long sessionVersion, String sessionName) throws SdpException {
		SessionDescription sdp = SdpFactoryImpl.getInstance()
			.createSessionDescription(localAddress);
		OriginField originField = createOriginField(sessionId,
			sessionVersion, localAddress);
		sdp.setOrigin(originField);
		SessionNameField sessionNameField = createSessionNameField(sessionName);
		sdp.setSessionName(sessionNameField);
		return sdp;
	}

	private OriginField createOriginField(long sessionId, long sessionVersion,
			String localAddress) throws SdpException {
		OriginField originField = new OriginField();
		originField.setSessionId(sessionId);
		originField.setSessionVersion(sessionVersion);
		originField.setUsername(identifier);
		originField.setAddress(localAddress);
		originField.setNetworkType(SDPKeywords.IN);
		originField.setAddressType(SDPKeywords.IPV4);
		return originField;
	}

	private SessionNameField createSessionNameField(String sessionName)
			throws SdpException {
		SessionNameField sessionNameField = new SessionNameField();
		sessionNameField.setSessionName(sessionName);
		return sessionNameField;
	}

	private SessionDescription includeOfferedMediaTypes(SessionDescription offer,
			Agent iceAgent) throws SdpException {
		Vector<String> allMediaFormats = new Vector<>();
		Vector<MediaDescription> mediaDescriptions = new Vector<>();
		generateOfferMediaDescriptions(SupportedMediaType.AUDIO,
			allMediaFormats, mediaDescriptions, iceAgent);
		generateOfferMediaDescriptions(SupportedMediaType.VIDEO,
			allMediaFormats, mediaDescriptions, iceAgent);
		offer.setMediaDescriptions(mediaDescriptions);
		IceSdpUtils.initSessionDescription(offer, iceAgent);
		if (!iceIsLocallySupported) {
			removeIceInformationFromSdp(offer);
		}
		logger.info("<< {{}} codecs were declared in offer {{}} >>",
			allMediaFormats, offer);
		return offer;
	}

	private void generateOfferMediaDescriptions(SupportedMediaType mediaType, Vector<String> allMediaFormats,
			Vector<MediaDescription> mediaDescriptions, Agent iceAgent)
			throws SdpException {
		final Set<MediaCodecInstance> mediaCodecs;
		if (mediaType == SupportedMediaType.VIDEO) {
			mediaCodecs = videoCodecs;
		} else {
			mediaCodecs = audioCodecs;
		}
		for (MediaCodecInstance mediaCodec : mediaCodecs) {
			if (!mediaCodec.isEnabled) {
				continue;
			}
			final String codecType = Integer.toString(mediaCodec.getType());
			allMediaFormats.add(codecType);
			MediaDescriptionImpl mediaDescription = new MediaDescriptionImpl();
			AttributeField rtpmapAttributeField = new AttributeField();
			rtpmapAttributeField.setName(SdpConstants.RTPMAP);
			rtpmapAttributeField.setValue(String.format(Locale.US, "%d %s",
				mediaCodec.getType(), mediaCodec.getRtpmap()));
			mediaDescription.addAttribute(rtpmapAttributeField);
			MediaField mediaField = new MediaField();
			Vector<String> mediaFormats = new Vector<>();
			mediaFormats.add(codecType);
			mediaField.setMediaFormats(mediaFormats);
			mediaField.setMedia(mediaType.name().toLowerCase());
			mediaField.setMediaType(mediaType.name().toLowerCase());
			mediaField.setProtocol(SdpConstants.RTP_AVP);
			mediaDescription.setMediaField(mediaField);
			int minPort = 16384;
			int maxPort = (32767 - 16384);
			int localPort = new Random().nextInt(maxPort) + minPort;
			IceMediaStream mediaStream = iceAgent
				.createMediaStream(mediaCodec.getRtpmap().toLowerCase());
			try {
				iceAgent.createComponent(mediaStream, Transport.UDP,
					localPort, minPort, minPort + maxPort);
				iceAgent.createComponent(mediaStream, Transport.UDP,
					localPort + 1, minPort, minPort + maxPort);
				IceSdpUtils.initMediaDescription(mediaDescription, mediaStream);
			} catch (IllegalArgumentException | IOException ignore) {
				ignore.printStackTrace();
			}
			AttributeField sendReceiveAttribute = new AttributeField();
			sendReceiveAttribute.setValue("sendrecv");
			mediaDescription.addAttribute(sendReceiveAttribute);
			mediaDescriptions.add(mediaDescription);
		}
	}

	@SuppressWarnings("unchecked")
	private SessionDescription includeAcceptedMediaTypes(String callId,
			SessionType sessionType, SessionDescription answer,
			SessionDescription offer, Agent iceAgent) throws SdpException {
		Vector<MediaDescription> offerMediaDescriptions = offer
			.getMediaDescriptions(false);
		if (offerMediaDescriptions == null || offerMediaDescriptions.isEmpty()) {
			return null;
		}
		Vector<String> allMediaFormats = new Vector<>();
		Vector<MediaDescription> answerMediaDescriptions = new Vector<>();
		generateAnswerMediaDescriptions(SupportedMediaType.AUDIO, offerMediaDescriptions,
			allMediaFormats, answerMediaDescriptions, iceAgent);
		generateAnswerMediaDescriptions(SupportedMediaType.VIDEO, offerMediaDescriptions,
			allMediaFormats, answerMediaDescriptions, iceAgent);
		if (answerMediaDescriptions.isEmpty()) {
			return null;
		}
		answer.setMediaDescriptions(answerMediaDescriptions);
		IceSdpUtils.initSessionDescription(answer, iceAgent);
		if (!iceIsLocallySupported) {
			removeIceInformationFromSdp(answer);
		}
		logger.info("<< {{}} codecs were declared in {} answer {{}} to {} offer {{}} >>",
			allMediaFormats, sessionType, answer, sessionType, offer);
		try {
			prepareForSessionSetup(callId, sessionType, offer, answer, iceAgent);
		} catch (Throwable anyIssue) {
			logger.error("%% {} could not prepare for {} session setup in "
				+ "context of call invitation {}! %%",
				pluginClass, sessionType, callId, anyIssue);
		}
		return answer;
	}

	@SuppressWarnings("unchecked")
	private void generateAnswerMediaDescriptions(SupportedMediaType mediaType,
			Vector<MediaDescription> offerMediaDescriptions, Vector<String> allMediaFormats,
			Vector<MediaDescription> answerMediaDescriptions, Agent iceAgent) throws SdpException {
		final Set<MediaCodecInstance> mediaCodecs;
		if (mediaType == SupportedMediaType.VIDEO) {
			mediaCodecs = videoCodecs;
		} else {
			mediaCodecs = audioCodecs;
		}
		for (MediaCodecInstance mediaCodec : mediaCodecs) {
			if (!mediaCodec.isEnabled) {
				continue;
			}
			//TODO remove those 3 lines above, but not before we update
			//Sipuada to understand that Answer SDPs will no longer be null
			//if Offer SDPs are non null in case the answerer wants to deny
			//all media streams and codecs, as each offered media stream/codec
			//entry will be repeated in the SDP but with the associated
			//port set to ZERO to indicate this.
			for (MediaDescription mediaDescription : offerMediaDescriptions) {
				Vector<AttributeField> attributeFields
					= ((MediaDescription) mediaDescription).getAttributes(false);
				boolean sendReceive = false, sendOnly = false, receiveOnly = false, inactive = false;
				for (AttributeField attributeField : attributeFields) {
					if (attributeField.getValue() == null
							|| attributeField.getValue().trim().isEmpty()) {
						continue;
					}
					String directionField = attributeField.getValue().trim().toLowerCase();
					if (directionField.equals("sendrecv")) {
						sendReceive = true;
					} else if (directionField.equals("sendonly")) {
						receiveOnly = true;
					} else if (directionField.equals("recvonly")) {
						sendOnly = true;
					} else if (directionField.equals("inactive")) {
						inactive = true;
					}
				}
				for (AttributeField attributeField : attributeFields) {
					if (attributeField.getName().equals(SdpConstants.RTPMAP)) {
						int type = Integer.parseInt(attributeField.getValue()
							.split(" ")[0].trim());
						String rtpmap = attributeField.getValue().split(" ")[1].trim();
						if ((type >= 0 && type <= 34 && type == mediaCodec.getType())
								|| rtpmap.toUpperCase().trim().equals(mediaCodec.getRtpmap())) {
							String codecType = Integer.toString(type);
							allMediaFormats.add(codecType);
							MediaDescriptionImpl cloneMediaDescription
								= new MediaDescriptionImpl();
							AttributeField rtpmapAttributeField = new AttributeField();
							rtpmapAttributeField.setName(SdpConstants.RTPMAP);
							rtpmapAttributeField.setValue(String.format
								(Locale.US, "%d %s",type, rtpmap.toUpperCase().trim()));
							cloneMediaDescription.addAttribute(rtpmapAttributeField);
							MediaField mediaField = new MediaField();
							Vector<String> mediaFormats = new Vector<>();
							mediaFormats.add(codecType);
							mediaField.setMediaFormats(mediaFormats);
							mediaField.setMedia(mediaType.name().toLowerCase());
							mediaField.setMediaType(mediaType.name().toLowerCase());
							mediaField.setProtocol(SdpConstants.RTP_AVP);
							cloneMediaDescription.setMediaField(mediaField);
							MediaCodecInstance supportedMediaCodec = null;
							for (MediaCodecInstance audioCodec : audioCodecs) {
								if (!audioCodec.isEnabled) {
									continue;
								}
								if (audioCodec.getRtpmap().toLowerCase().equals
									(rtpmap.toLowerCase().trim())) {
									supportedMediaCodec = audioCodec;
									break;
								}
							}
							for (MediaCodecInstance videoCodec : videoCodecs) {
								if (!videoCodec.isEnabled) {
									continue;
								}
								if (videoCodec.getRtpmap().toLowerCase().equals
									(rtpmap.toLowerCase().trim())) {
									supportedMediaCodec = videoCodec;
									break;
								}
							}
							logger.debug("<< {} is the supported codec! >>",
								supportedMediaCodec);
							int minPort = 16384;
							int maxPort = (32767 - 16384);
							final int localPort;
							if (supportedMediaCodec != null) {
								localPort = new Random().nextInt(maxPort) + minPort;
							} else {
								localPort = 0;
							}
							if (supportedMediaCodec != null) {
								IceMediaStream mediaStream = iceAgent.createMediaStream
									(supportedMediaCodec.getRtpmap().toLowerCase());
								try {
									iceAgent.createComponent(mediaStream, Transport.UDP,
										localPort, minPort, minPort + maxPort);
									iceAgent.createComponent(mediaStream, Transport.UDP,
										localPort + 1, minPort, minPort + maxPort);
									IceSdpUtils.initMediaDescription
										(cloneMediaDescription, mediaStream);
								} catch (IllegalArgumentException | IOException ignore) {
									ignore.printStackTrace();
								}
								final AttributeField directionAttribute = new AttributeField();
								if (sendReceive) {
									directionAttribute.setValue("sendrecv");
								} else if (receiveOnly) {
									directionAttribute.setValue("recvonly");
								} else if (sendOnly) {
									directionAttribute.setValue("sendonly");
								} else if (inactive) {
									directionAttribute.setValue("inactive");
								} else {
									directionAttribute.setValue("sendrecv");
								}
								cloneMediaDescription.addAttribute(directionAttribute);
							}
							answerMediaDescriptions.add(cloneMediaDescription);
						}
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void removeIceInformationFromSdp(SessionDescription sdp) {
		try {
			Vector<MediaDescription> mediaDescriptions = sdp.getMediaDescriptions(true);
			for (MediaDescription mediaDescription : mediaDescriptions) {
				mediaDescription.removeAttribute(IceSdpUtils.MID);
				while (mediaDescription.getAttribute(CandidateAttribute.NAME) != null) {
					mediaDescription.removeAttribute(CandidateAttribute.NAME);
				}
			}
			sdp.removeAttribute(IceSdpUtils.ICE_OPTIONS);
			sdp.removeAttribute(IceSdpUtils.ICE_UFRAG);
			sdp.removeAttribute(IceSdpUtils.ICE_PWD);
		} catch (SdpException ignore) {}
	}

	interface ExtractionCallback {

		void onConnectionInfoExtracted(String dataAddress, int dataPort,
			String controlAddress, int controlPort, String rtpmap,
			int codecType, MediaDirection direction, boolean peerSupportsIce);

		void onExtractionIgnored(String rtpmap, int codecType);

		void onExtractionPartiallyFailed(Throwable anyIssue);

		void onExtractionFailedCompletely(Throwable anyIssue);

		void onDoneExtractingConnectionInfo();

		String getRole();

		String getSdpType();

	}

	private abstract class ExtractionCallbackImpl implements ExtractionCallback {

		private final String role;
		private final String sdpType;

		public ExtractionCallbackImpl(String role, String sdpType) {
			this.role = role;
			this.sdpType = sdpType;
		}

		@Override
		public abstract void onConnectionInfoExtracted(String dataAddress,
			int dataPort, String controlAddress, int controlPort, String rtpmap,
			int codecType, MediaDirection direction, boolean peerSupportsIce);

		@Override
		public final void onExtractionIgnored(String rtpmap, int codecType) {
			logger.error("%% {{}} as {} ignored extraction of {} "
				+ "media description {{}} - code: {{}} as it "
				+ "contained no connection info. %%", pluginClass,
				role, sdpType, rtpmap, codecType);
		}

		@Override
		public final void onExtractionPartiallyFailed(Throwable anyIssue) {
			logger.error("%% {{}} as {} partially failed during "
				+ "extraction of {} media description line. %%",
				pluginClass, role, sdpType, anyIssue);
		}

		@Override
		public final void onExtractionFailedCompletely(Throwable anyIssue) {
			logger.error("%% {{}} as {} failed completely before "
				+ "extraction of {} media description lines. %%",
				pluginClass, role, sdpType, anyIssue);
		}

		@Override
		public void onDoneExtractingConnectionInfo() {};

		@Override
		public String getRole() {
			return role;
		}

		@Override
		public String getSdpType() {
			return sdpType;
		}

	}

	private void prepareForSessionSetup(final String callId, final SessionType type,
			final SessionDescription offer, final SessionDescription answer, final Agent iceAgent)
					throws SdpException {
		final CallRole actualRole = roles.get(getSessionKey(callId, type));
		extractConnectionInformation(answer, callId, type, iceAgent, actualRole == CallRole.CALLER,
				new ExtractionCallbackImpl(roles.get(getSessionKey(callId, type)).toString(), "ANSWER") {

			@Override
			public void onConnectionInfoExtracted(final String answerDataAddress,
					final int answerDataPort, final String answerControlAddress,
					final int answerControlPort, final String answerRtpmap,
					final int answerCodecType, final MediaDirection answerDirection,
					final boolean iceIsSupportedByAnswererPeer) {
				extractConnectionInformation(offer, callId, type, iceAgent, actualRole == CallRole.CALLEE,
						new ExtractionCallbackImpl(roles.get(getSessionKey(callId, type)).toString(), "OFFER") {

					@Override
					public void onConnectionInfoExtracted(final String offerDataAddress,
							final int offerDataPort, final String offerControlAddress,
							final int offerControlPort, final String offerRtpmap,
							final int offerCodecType, final MediaDirection offerDirection,
							final boolean iceIsSupportedByOffererPeer) {
						if (offerRtpmap.toLowerCase().trim().equals
								(answerRtpmap.toLowerCase().trim())) {
							MediaCodecInstance supportedMediaCodec = null;
							for (MediaCodecInstance audioCodec : audioCodecs) {
								if (!audioCodec.isEnabled) {
									continue;
								}
								if (audioCodec.getRtpmap().toLowerCase().equals
									(answerRtpmap.toLowerCase().trim())) {
									supportedMediaCodec = audioCodec;
									break;
								}
							}
							for (MediaCodecInstance videoCodec : videoCodecs) {
								if (!videoCodec.isEnabled) {
									continue;
								}
								if (videoCodec.getRtpmap().toLowerCase().equals
									(answerRtpmap.toLowerCase().trim())) {
									supportedMediaCodec = videoCodec;
									break;
								}
							}
							logger.debug("%% {} is the supported codec! %%",
								supportedMediaCodec);
							if (supportedMediaCodec == null) {
								logger.error("%% {} FOUND A CODEC MATCH but said codec"
									+ " {} is not supported by this plugin!(?!) %%",
									pluginClass, answerRtpmap + " - " + answerCodecType);
								return;
							} else {
								logger.error("%% {} FOUND A CODEC MATCH: {}! %%",
									pluginClass, answerRtpmap + " - " + answerCodecType);
							}
							final MediaCodecInstance mediaCodecOfInterest = supportedMediaCodec;
							final IceMediaStream mediaStream = iceAgent.getStream
								(mediaCodecOfInterest.getRtpmap().toLowerCase());
							final Component rtpComponent = mediaStream == null
								? null : mediaStream.getComponent(Component.RTP);
							final Component rtcpComponent = mediaStream == null
								? null : mediaStream.getComponent(Component.RTCP);
							if (!iceIsSupportedByOffererPeer || !iceIsSupportedByAnswererPeer) {
								cleanUpIceAgent(callId, type, iceAgent,
									mediaStream, rtpComponent, rtcpComponent);
								doPrepareStream(callId, type, actualRole, supportedMediaCodec,
									offerDirection, offerDataAddress, offerDataPort,
									offerControlAddress, offerControlPort,
									answerDirection, answerDataAddress, answerDataPort,
									answerControlAddress, answerControlPort);
								return;
							}
							iceAgent.addStateChangeListener(new PropertyChangeListener() {

								@Override
								public synchronized void propertyChange(PropertyChangeEvent event) {
									logger.debug("ICE4J:propertyChange: {}", event);
									synchronized (this) {
										if (event.getSource() instanceof Agent) {
											Agent agent = (Agent) event.getSource();
											logger.debug("ICE4J:agent.getState(): {}", agent.getState());
											if (agent.getState().equals(IceProcessingState.TERMINATED)) {
												if (mediaStream == null) {
													return;
												}
												logger.debug("ICE processing finalized successfully, "
													+ "preparing stream using best addresses...");
												CandidatePair rtpPair = rtpComponent.getSelectedPair();
												TransportAddress rtpTransportAddress = rtpPair
													.getRemoteCandidate().getTransportAddress();
												String remoteDataAddress = rtpTransportAddress
													.getAddress().getHostAddress();
												int remoteDataPort = rtpTransportAddress.getPort();
												CandidatePair rtcpPair = rtcpComponent.getSelectedPair();
												TransportAddress rtcpTransportAddress = rtcpPair
													.getRemoteCandidate().getTransportAddress();
												String remoteControlAddress = rtcpTransportAddress
													.getAddress().getHostAddress();
												int remoteControlPort = rtcpTransportAddress.getPort();
												iceAgent.removeStateChangeListener(this);
												cleanUpIceAgent(callId, type, iceAgent,
													mediaStream, rtpComponent, rtcpComponent);
												doPrepareStream(callId, type, actualRole, mediaCodecOfInterest,
													offerDirection, offerDataAddress, offerDataPort,
													offerControlAddress, offerControlPort,
													answerDirection, answerDataAddress, answerDataPort,
													answerControlAddress, answerControlPort,
													remoteDataAddress, remoteDataPort,
													remoteControlAddress, remoteControlPort);
											} else if (agent.getState().equals(IceProcessingState.FAILED)) {
												logger.debug("ICE processing FAILED! Preparing stream "
													+ "using the default addresses available...");
												iceAgent.removeStateChangeListener(this);
												cleanUpIceAgent(callId, type, iceAgent,
													mediaStream, rtpComponent, rtcpComponent);
												doPrepareStream(callId, type, actualRole, mediaCodecOfInterest,
													offerDirection, offerDataAddress, offerDataPort,
													offerControlAddress, offerControlPort,
													answerDirection, answerDataAddress, answerDataPort,
													answerControlAddress, answerControlPort);
											}
										}
									}
								}

							});
						}
					}

				});
			}

			@Override
			public void onDoneExtractingConnectionInfo() {
				if (!iceAgent.isOver()) {
					logger.debug("%% Just finished extracting some "
						+ "connection info so starting ICE processing... %%");
					iceAgent.startConnectivityEstablishment();
				}
			}

		});
	}

	@SuppressWarnings("unchecked")
	private void extractConnectionInformation
			(SessionDescription sdp, String callId, SessionType type, Agent iceAgent,
			final boolean shouldParseCandidatesAndFeedIceAgent, ExtractionCallback callback) {
		logger.debug("%% {} will extract connection info from {} sdp as {}! %%",
			pluginClass, callback.getSdpType(), callback.getRole());
		String possibleParentDataAddress = null;
		try {
			if (sdp.getConnection() != null) {
				possibleParentDataAddress = sdp.getConnection().getAddress();
			} else {
				logger.debug("%% {} could not find parent data connection "
					+ "address: {}! %%", pluginClass, possibleParentDataAddress);
			}
		} catch (Throwable anyIssue) {
			logger.debug("%% {} could not find parent data connection "
				+ "address: {}! %%", pluginClass, possibleParentDataAddress);
		}
		final String parentDataAddress = possibleParentDataAddress;
		String possibleParentControlConnection
			= retrieveControlConnectionInfo(sdp);
		final String parentControlAddress = possibleParentControlConnection == null
			|| !possibleParentControlConnection.contains("\\:")
			? null : possibleParentControlConnection.split("\\:")[0].trim().isEmpty()
			? null : possibleParentControlConnection.split("\\:")[0];
		final String parentControlPort = possibleParentControlConnection == null
			|| !possibleParentControlConnection.contains("\\:")
			? null : possibleParentControlConnection.split("\\:")[1].trim().isEmpty()
			? null : possibleParentControlConnection.split("\\:")[1];
		if (possibleParentControlConnection == null) {
			logger.debug("%% {} could not find parent "
				+ "control connection info! %%", pluginClass);
		}
		final Vector<MediaDescription> mediaDescriptions;
		try {
			mediaDescriptions = sdp.getMediaDescriptions(false);
		} catch (Throwable anyIssue) {
			callback.onExtractionFailedCompletely(anyIssue);
			return;
		}
		if (mediaDescriptions == null) {
			logger.debug("%% {} could not find any "
				+ "media descriptions! %%", pluginClass);
			return;
		}
		boolean someConnectionInfoExtractedSuccessfully = true;
		for (MediaDescription mediaDescription : mediaDescriptions) {
			Vector<AttributeField> attributeFields
				= ((MediaDescription) mediaDescription).getAttributes(true);
			MediaDirection direction = MediaDirection.SENDRECV;
			for (AttributeField attributeField : attributeFields) {
				try {
					if (attributeField.getValue() == null
							|| attributeField.getValue().trim().isEmpty()) {
						continue;
					}
					String directionField = attributeField.getValue().trim().toLowerCase();
					if (directionField.equals("sendrecv")) {
						direction = MediaDirection.SENDRECV;
					} else if (directionField.equals("sendonly")) {
						direction = MediaDirection.SENDONLY;
					} else if (directionField.equals("recvonly")) {
						direction = MediaDirection.RECVONLY;
					} else if (directionField.equals("inactive")) {
						direction = MediaDirection.INACTIVE;
					}
				} catch (SdpParseException ignore) {}
			}
			for (AttributeField attributeField : attributeFields) {
				try {
					logger.debug("%% Parsing attribute field line: {{}}... %%",
						attributeField.toString().trim());
					if (attributeField.getName() != null
							&& attributeField.getName().equals(SdpConstants.RTPMAP)) {
						logger.debug("%% It is a RTPMAP line! %%");
						int codecType = Integer.parseInt(attributeField
							.getValue().split(" ")[0].trim());
						String rtpmap = attributeField.getValue().split(" ")[1].trim();
						logger.debug("%% RTPMAP: {} --- CodecType: {}! %%",
							rtpmap, codecType);

						final Connection connection = mediaDescription.getConnection();
						final Media media = mediaDescription.getMedia();
						String dataAddress;
						int dataPort;
						if (media == null || (parentDataAddress == null
								&& connection == null)) {
							callback.onExtractionIgnored(rtpmap, codecType);
							continue;
						} else if (connection == null) {
							dataAddress = parentDataAddress;
							dataPort = media.getMediaPort();
							logger.debug("%% RTPMAP line contains no connection info, "
								+ "so considering SDP parent connection: {}:{}! %%",
								dataAddress, dataPort);
						} else {
							dataAddress = connection.getAddress();
							dataPort = media.getMediaPort();
							logger.debug("%% RTPMAP line contains connection info, "
								+ "so considering it: {}:{}! %%",
								dataAddress, dataPort);
						}
						String possibleControlConnection
							= retrieveControlConnectionInfo(mediaDescription);
						if (possibleControlConnection == null) {
							logger.debug("%% {} could not find media "
								+ "control connection info! %%",
								pluginClass);
						}
						String controlAddress = possibleControlConnection == null
							|| !possibleControlConnection.contains("\\:")
							|| possibleControlConnection.split("\\:")[0].isEmpty()
							? parentControlAddress != null ? parentControlAddress
							: dataAddress : possibleControlConnection.split("\\:")[0];
						int controlPort = possibleControlConnection == null
							|| !possibleControlConnection.contains("\\:")
							|| possibleControlConnection.split("\\:")[1].isEmpty()
							? parentControlPort == null ? dataPort + 1
							: Integer.parseInt(parentControlPort) : Integer
							.parseInt(possibleControlConnection.split("\\:")[1]);

						IceMediaStream relatedIceStream = iceAgent.isOver()
							? null : iceAgent.getStream(rtpmap.toLowerCase());
						Component rtpComponent = null, rtcpComponent = null;
						if (relatedIceStream != null) {
				            rtpComponent = relatedIceStream.getComponent(Component.RTP);
				            rtcpComponent = relatedIceStream.getComponent(Component.RTCP);
						}
						if (shouldParseCandidatesAndFeedIceAgent) {
							logger.debug("%% Shall handle this remote SDP "
								+ "to extract relevant ICE information! %%");
						} else {
							logger.debug("%% Shall handle this local SDP "
								+ "to extract relevant ICE information! %%");
						}
						logger.debug("%% But are we done with the associated "
							+ "Ice Agent? R: {} %%", iceAgent.isOver());
						if (!iceAgent.isOver()) {
							logger.debug("%% Current Ice Agent streams: {} %%",
								iceAgent.getStreams());
						}
						boolean sdpSupportsIce = iceIsLocallySupported;
			            if (shouldParseCandidatesAndFeedIceAgent && relatedIceStream != null
								&& (rtpComponent != null && rtpComponent
									.getDefaultRemoteCandidate() == null)
								&& (rtcpComponent == null || rtcpComponent
									.getDefaultRemoteCandidate() == null)) {
							sdpSupportsIce = false;
							for (AttributeField candidateAttributeField : attributeFields) {
								if (candidateAttributeField.getName() != null
										&& candidateAttributeField.getName()
											.equals(CandidateAttribute.NAME)) {
					            	logger.debug("%% Remote SDP contains some ICE candidate"
										+ " information: {} %%", candidateAttributeField);
									sdpSupportsIce = true;
								}
							}
							if (!sdpSupportsIce) {
				            	logger.debug("%% Remote SDP contains no ICE information, "
			            			+ "so ICE processing shall be skipped. %%");
							} else {
				            	logger.debug("%% About to attempt extracting relevant"
									+ " ICE information from remote SDP. %%");
				            	String remoteIceUsernameFragment = mediaDescription
			            			.getAttribute(IceSdpUtils.ICE_UFRAG);
				            	if (remoteIceUsernameFragment == null
				            			|| remoteIceUsernameFragment.trim().isEmpty()) {
				            		remoteIceUsernameFragment = sdp.getAttribute(IceSdpUtils.ICE_UFRAG);
				            	}
			                	relatedIceStream.setRemoteUfrag(remoteIceUsernameFragment);
				            	String remoteIcePassword = mediaDescription
			            			.getAttribute(IceSdpUtils.ICE_PWD);
				            	if (remoteIcePassword == null
				            			|| remoteIcePassword.trim().isEmpty()) {
				            		remoteIcePassword = sdp.getAttribute(IceSdpUtils.ICE_PWD);
				            	}
				            	relatedIceStream.setRemotePassword(remoteIcePassword);
			                	logger.debug("%% Remote ICE username fragment: {} set"
		                			+ " into related ICE media stream. %%", remoteIceUsernameFragment);
			                	logger.debug("%% Remote ICE password: {} set"
		                			+ " into related ICE media stream. %%", remoteIcePassword);
					            TransportAddress defaultRemoteRtpAddress = new TransportAddress
				            		(dataAddress, dataPort, Transport.UDP);
					            Candidate<?> defaultRtpCandidate = rtpComponent
				            		.findRemoteCandidate(defaultRemoteRtpAddress);
					            rtpComponent.setDefaultRemoteCandidate(defaultRtpCandidate);
								logger.debug("%% Related ICE stream's RTP component:"
									+ " {}:{} ({}) %%", rtpComponent, dataAddress, dataPort);
					            if (rtcpComponent != null) {
						            TransportAddress defaultRemoteRtcpAddress = new TransportAddress
					            		(controlAddress, controlPort, Transport.UDP);
						            Candidate<?> defaultRtcpCandidate = rtpComponent
					            		.findRemoteCandidate(defaultRemoteRtcpAddress);
						            rtcpComponent.setDefaultRemoteCandidate(defaultRtcpCandidate);
									logger.debug("%% Related ICE stream's RTCP component:"
										+ " ({}:{}) {} %%", rtcpComponent, controlAddress, controlPort);
					            }
								for (AttributeField candidateAttributeField : attributeFields) {
									if (candidateAttributeField.getName() != null
											&& candidateAttributeField.getName()
											.equals(CandidateAttribute.NAME)) {
										retrieveCandidateInfoAndBindToAgentIfApplicable
											(relatedIceStream, candidateAttributeField);
									}
								}
							}
			            } else if (!shouldParseCandidatesAndFeedIceAgent
			            		&& relatedIceStream != null && rtpComponent != null) {
			            	logger.debug("%% About to attempt extracting relevant"
								+ " ICE information from local SDP. %%");
			            	for (LocalCandidate localCandidate
			            			: rtpComponent.getLocalCandidates()) {
			            		if (localCandidate.getType()
			            				== CandidateType.HOST_CANDIDATE) {
			            			dataAddress = localCandidate
		            					.getTransportAddress().getHostAddress();
			            			dataPort = localCandidate
		            					.getTransportAddress().getPort();
			            			break;
			            		}
			            	}
			            	if (rtcpComponent != null) {
				            	for (LocalCandidate localCandidate
				            			: rtcpComponent.getLocalCandidates()) {
				            		if (localCandidate.getType()
				            				== CandidateType.HOST_CANDIDATE) {
				            			controlAddress = localCandidate
			            					.getTransportAddress().getHostAddress();
				            			controlPort = localCandidate
			            					.getTransportAddress().getPort();
				            			break;
				            		}
				            	}
			            	}
			            }
						callback.onConnectionInfoExtracted(dataAddress, dataPort,
							controlAddress, controlPort, rtpmap, codecType,
							direction, sdpSupportsIce);
						someConnectionInfoExtractedSuccessfully = true;
					}
				} catch (Throwable anyIssue) {
					callback.onExtractionPartiallyFailed(anyIssue);
					someConnectionInfoExtractedSuccessfully = false;
				}
			}
		}
		if (someConnectionInfoExtractedSuccessfully) {
			callback.onDoneExtractingConnectionInfo();
		}
	}

	private String retrieveControlConnectionInfo(SessionDescription sdp) {
		try {
			return retrieveControlConnectionInfo(sdp.getAttribute("rtcp"));
		} catch (Throwable anyIssue) {
			return null;
		}
	}

	private String retrieveControlConnectionInfo(MediaDescription mediaDescription) {
		try {
			return retrieveControlConnectionInfo
				(mediaDescription.getAttribute("rtcp"));
		} catch (Throwable anyIssue) {
			return null;
		}
	}

	private String retrieveControlConnectionInfo(String connectionLine) {
		String controlAddress = "";
		String controlPort = "";
		if (connectionLine != null) {
			connectionLine = connectionLine.trim();
			try {
				Integer.parseInt(connectionLine);
				controlPort = connectionLine;
			} catch (Throwable anyIssue) {
				controlAddress = connectionLine.split(" ")
					[connectionLine.length() - 1].trim();
				controlPort = connectionLine.split(" ")[0].trim();
				try {
					Integer.parseInt(controlPort);
					for (int i=0; i<controlAddress.length(); i++) {
						char thisChar = controlAddress.charAt(i);
						if (!(thisChar == '.'
								|| Character.isDigit(thisChar))) {
							throw new Exception();
						}
					}
				} catch (Throwable anyOtherIssue) {
					return null;
				}
			}
		}
		return String.format(Locale.US, "%s:%s", controlAddress, controlPort);
	}

	private void cleanUpIceAgent(String callId, SessionType type,
			Agent iceAgent, IceMediaStream mediaStream,
			Component rtpComponent, Component rtcpComponent) {
		if (mediaStream != null) {
			if (rtpComponent != null) {
				mediaStream.removeComponent(rtpComponent);
			}
			if (rtcpComponent != null) {
				mediaStream.removeComponent(rtcpComponent);
			}
		}
		if (iceAgent != null) {
			if (mediaStream != null) {
				iceAgent.removeStream(mediaStream);
			}
			if (iceAgent.getStreamCount() == 0) {
				iceAgents.remove(getSessionKey(callId, type));
				iceAgent.free();
			}
		}
	}

	private void retrieveCandidateInfoAndBindToAgentIfApplicable
			(IceMediaStream relatedIceStream, AttributeField candidateAttribute) {
		String fullCandidateAttributeValue;
        try {
            fullCandidateAttributeValue = candidateAttribute.getValue();
        } catch (Throwable ignore){
        	ignore.printStackTrace();
        	return;
        }

        logger.debug("%% About to parse candidate line: {} %%", fullCandidateAttributeValue);
        String[] candidateAttributeValueParts = fullCandidateAttributeValue.trim().split("\\s+");
        String foundation = candidateAttributeValueParts[0];
        int componentId = Integer.parseInt(candidateAttributeValueParts[1]);
        Transport transport = Transport.parse(candidateAttributeValueParts[2]);
        long priority = Long.parseLong(candidateAttributeValueParts[3]);
        String address = candidateAttributeValueParts[4];
        int port = Integer.parseInt(candidateAttributeValueParts[5]);
        CandidateType type = CandidateType.parse(candidateAttributeValueParts[7]);
        String relatedAddress = candidateAttributeValueParts.length > 8
    		? candidateAttributeValueParts[9] : null;
        int relatedPort = candidateAttributeValueParts.length > 10
    		? Integer.parseInt(candidateAttributeValueParts[11]) : -1;

        Component component = relatedIceStream.getComponent(componentId);
        if (component == null) {
    		logger.debug("%% No component of id {} is related to {} ICE stream ({}). %%",
				componentId, relatedIceStream.getName(), relatedIceStream);
        	return;
        }
		logger.debug("%% Component of id {} is related to {} ICE stream ({}). %%",
			componentId, relatedIceStream.getName(), relatedIceStream);

        TransportAddress transportAddress = new TransportAddress(address, port, transport);
        RemoteCandidate relatedCandidate = null;
        if (relatedAddress != null && relatedPort != -1) {
            TransportAddress relatedTransportAddress = new TransportAddress
        		(relatedAddress, relatedPort, Transport.UDP);
        	relatedCandidate = component.findRemoteCandidate(relatedTransportAddress);
        }

        for (RemoteCandidate remoteCandidate : component.getRemoteCandidates()) {
        	if (remoteCandidate.getFoundation().equals(foundation)
        			&& remoteCandidate.getPriority() == priority
        			&& remoteCandidate.getTransportAddress().equals(transportAddress)
        			&& ((remoteCandidate.getRelatedCandidate() == null && relatedCandidate == null)
        			|| (remoteCandidate.getRelatedCandidate() != null && relatedCandidate != null
        			&& remoteCandidate.getRelatedCandidate().equals(relatedCandidate)))
        			&& remoteCandidate.getType() == type) {
        		return;
        	}
        }

        logger.debug("%% ICE remote candidate (address:[{}:{}], type:[{}],"
        	+ " priority:[{}], foundation:[{}], relatedCandidate:[{}])"
        	+ " will be added to component. %%", transportAddress.getHostAddress(),
        	transportAddress.getPort(), type, priority, foundation, relatedCandidate);
        RemoteCandidate remoteCandidate = new RemoteCandidate(transportAddress,
    		component, type, foundation, priority, relatedCandidate);
        component.addRemoteCandidate(remoteCandidate);
	}

	private synchronized void doPrepareStream
		(String callId, SessionType type, CallRole actualRole,
		MediaCodecInstance mediaCodecOfInterest,
		MediaDirection offerDirection,
		String offerDataAddress, int offerDataPort,
		String offerControlAddress, int offerControlPort,
		MediaDirection answerDirection,
		String answerDataAddress, int answerDataPort,
		String answerControlAddress, int answerControlPort) {
		doPrepareStream(callId, type, actualRole, mediaCodecOfInterest,
			offerDirection, offerDataAddress, offerDataPort,
			offerControlAddress, offerControlPort,
			answerDirection, answerDataAddress, answerDataPort,
			answerControlAddress, answerControlPort,
			null, -1, null, -1);
	}

	private synchronized void doPrepareStream
			(String callId, SessionType type, CallRole actualRole,
			MediaCodecInstance mediaCodecOfInterest,
			MediaDirection offerDirection,
			String offerDataAddress, int offerDataPort,
			String offerControlAddress, int offerControlPort,
			MediaDirection answerDirection,
			String answerDataAddress, int answerDataPort,
			String answerControlAddress, int answerControlPort,
			String remoteDataAddress, int remoteDataPort,
			String remoteControlAddress, int remoteControlPort) {
		if (!preparedStreams.containsKey(getSessionKey(callId, type))) {
			preparedStreams.put(getSessionKey(callId, type),
				new HashMap<MediaCodecInstance, Session>());
		}
		switch (actualRole) {
			case CALLER:
				if ((remoteDataAddress == null || remoteDataPort == -1
						|| remoteControlAddress == null || remoteControlPort == -1)
						|| (answerDataAddress.equals(remoteDataAddress)
						&& answerDataPort == remoteDataPort
						&& answerControlAddress.equals(remoteControlAddress)
						&& answerControlPort == remoteControlPort)) {
					logger.debug("%% Prepared a {} ({}) stream from "
						+ "[rtp = {}:{}, rtcp = {}:{}] to [rtp = {}:{},"
						+ " rtcp = {}:{}]! %%", mediaCodecOfInterest,
						offerDirection, offerDataAddress, offerDataPort,
						offerControlAddress, offerControlPort, answerDataAddress,
						answerDataPort, answerControlAddress, answerControlPort);
					preparedStreams.get(getSessionKey(callId, type)).put
						(mediaCodecOfInterest, new Session(offerDataAddress,
						offerDataPort, offerControlAddress, offerControlPort,
						answerDataAddress, answerDataPort, answerControlAddress,
						answerControlPort, offerDirection));
				} else {
					logger.debug("%% Prepared a {} ({}) stream from "
						+ "[rtp = {}:{}, rtcp = {}:{}] to [rtp = {}:{} --> {}:{},"
						+ " rtcp = {}:{} --> {}:{}]! %%", mediaCodecOfInterest,
						offerDirection, offerDataAddress, offerDataPort,
						offerControlAddress, offerControlPort, answerDataAddress,
						answerDataPort, remoteDataAddress, remoteDataPort,
						answerControlAddress, answerControlPort,
						remoteControlAddress, remoteControlPort);
					preparedStreams.get(getSessionKey(callId, type)).put
						(mediaCodecOfInterest, new Session(offerDataAddress,
						offerDataPort, offerControlAddress, offerControlPort,
						remoteDataAddress, remoteDataPort, remoteControlAddress,
						remoteControlPort, offerDirection));
				}
				break;
			case CALLEE:
				if ((remoteDataAddress == null || remoteDataPort == -1
						|| remoteControlAddress == null || remoteControlPort == -1)
						|| (offerDataAddress.equals(remoteDataAddress)
						&& offerDataPort == remoteDataPort
						&& offerControlAddress.equals(remoteControlAddress)
						&& offerControlPort == remoteControlPort)) {
					logger.debug("%% Prepared a {} ({}) stream from "
						+ "[rtp = {}:{}, rtcp = {}:{}] to [rtp = {}:{},"
						+ " rtcp = {}:{}]! %%", mediaCodecOfInterest,
						answerDirection, answerDataAddress, answerDataPort,
						answerControlAddress, answerControlPort, offerDataAddress,
						offerDataPort, offerControlAddress, offerControlPort);
					preparedStreams.get(getSessionKey(callId, type)).put
						(mediaCodecOfInterest, new Session(answerDataAddress,
						answerDataPort, answerControlAddress, answerControlPort,
						offerDataAddress, offerDataPort, offerControlAddress,
						offerControlPort, answerDirection));
				} else {
					logger.debug("%% Prepared a {} ({}) stream from "
						+ "[rtp = {}:{}, rtcp = {}:{}] to [rtp = {}:{} --> {}:{},"
						+ " rtcp = {}:{} --> {}:{}]! %%", mediaCodecOfInterest,
						answerDirection, answerDataAddress, answerDataPort,
						answerControlAddress, answerControlPort, offerDataAddress,
						offerDataPort, remoteDataAddress, remoteDataPort,
						offerControlAddress, offerControlPort,
						remoteControlAddress, remoteControlPort);
					preparedStreams.get(getSessionKey(callId, type)).put
						(mediaCodecOfInterest, new Session(answerDataAddress,
						answerDataPort, answerControlAddress, answerControlPort,
						remoteDataAddress, remoteDataPort, remoteControlAddress,
						remoteControlPort, answerDirection));
				}
				break;
		}
		if (postponedStreams.containsKey(getSessionKey(callId, type))) {
			performSessionSetup(callId, type, postponedStreams
				.get(getSessionKey(callId, type)));
		} else {
			logger.debug("%% If there was a scheduled setup, it was canceled,"
				+ " so, prepared {} ({}) stream discarded. Otherwise, stream "
				+ "was prepared and is ready to be setup. %%",
				mediaCodecOfInterest, answerDirection);
		}
	}

	/**
	 * Checks if there's an ongoing session of given type associated with given callId.
	 * @return true if plug-in has an ongoing session associated with given callId and type.
	 */
	public boolean isSessionOngoing(String callId, SessionType type) {
		return startedStreams.containsKey(getSessionKey(callId, type))
			&& startedStreams.get(getSessionKey(callId, type));
	}

	/**
	 * Perform session setup since offer/answer sent alongside
	 * a call invitation request/response with given callId
	 * was established successfully (or is expected to).
	 * The UserAgent is passed along so that the plug-in can ask
	 * it to perform session modification requests in the future.
	 * @return true if the plug-in could setup the session, false otherwise.
	 */
	public synchronized boolean performSessionSetup(String callId, SessionType type, SipUserAgent userAgent) {
		logger.debug("===*** performSessionSetup -> {}", getSessionKey(callId, type));
		synchronized (this) {
			Record record = records.get(getSessionKey(callId, type));
			SessionDescription offer = record != null ? record.getOffer() : null;
			SessionDescription answer = record != null ? record.getAnswer() : null;
			if (record == null || offer == null || answer == null) {
				logger.info("^^ {} aborted session setup attempt in context of call {}"
					+ " and will rely upon new offer/answer exchange initiated"
					+ " by a recently sent UPDATE request...\n"
					+ "Role: {{}}\nOffer: {{}}\nAnswer: {{}} ^^",
					pluginClass, callId, roles.get(getSessionKey
						(callId, type)), offer, answer);
				return true;
			} else if (!preparedStreams.containsKey((getSessionKey(callId, type)))) {
				logger.info("^^ {} postponed session setup in context of call {}...\n"
					+ "Role: {{}}\nOffer: {{}}\nAnswer: {{}} ^^",
					pluginClass, callId, roles.get(getSessionKey
						(callId, type)), offer, answer);
				postponedStreams.put(getSessionKey(callId, type), userAgent);
				return true;
			}
			logger.info("^^ {} performing session setup in context of call {}...\n"
				+ "Role: {{}}\nOffer: {{}}\nAnswer: {{}} ^^",
				pluginClass, callId, roles.get(getSessionKey
					(callId, type)), offer, answer);
			doSetupPreparedStreams(callId, type, preparedStreams);
			postponedStreams.remove(getSessionKey(callId, type));
			startedStreams.put(getSessionKey(callId, type), true);
			return true;
		}
	}

	protected abstract void doSetupPreparedStreams(String callId, SessionType type,
		Map<String, Map<MediaCodecInstance, Session>> preparedStreams);

	/**
	 * Perform session termination since offer/answer sent alongside
	 * a call invitation request/response with given callId could not
	 * establish a call or established a call that was recently finished.
	 * @return true if the plug-in could terminate the session, false otherwise.
	 */
	public synchronized boolean performSessionTermination(String callId, SessionType type) {
		logger.debug("===*** performSessionTermination -> {}", getSessionKey(callId, type));
		synchronized (this) {
			records.remove(getSessionKey(callId, type));
			if (preparedStreams.get(getSessionKey(callId, type)) != null) {
				logger.info("^^ {} performing session tear down in context "
					+ "of call {}... ^^", pluginClass, callId);
				doTerminateStreams(callId, type, preparedStreams);
				preparedStreams.remove(getSessionKey(callId, type));
			} else {
				logger.info("^^ {} canceled session setup "
					+ "in context of call {}. ^^",
					pluginClass, callId);
			}
			postponedStreams.remove(getSessionKey(callId, type));
			startedStreams.put(getSessionKey(callId, type), false);
			return true;
		}
	}

	protected abstract void doTerminateStreams(String callId, SessionType type,
		Map<String, Map<MediaCodecInstance, Session>> ongoingStreams);

	protected String getSessionKey(String callId, SessionType type) {
		return String.format(Locale.US, "%s_(%s)", callId, type);
	}

}
