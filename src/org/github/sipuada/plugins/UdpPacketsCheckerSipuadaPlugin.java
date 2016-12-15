package org.github.sipuada.plugins;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UdpPacketsCheckerSipuadaPlugin extends SipuadaPlugin {

	private enum AvailableMediaCodec implements SupportedMediaCodec {

		PCMA_8("PCMA", 8, 8000, SupportedMediaType.AUDIO, SessionType.REGULAR, true),
		SPEEX_8("SPEEX", 97, 8000, SupportedMediaType.AUDIO, SessionType.REGULAR, false),
		SPEEX_16("SPEEX", 97, 16000, SupportedMediaType.AUDIO, SessionType.REGULAR, false),
		SPEEX_32("SPEEX", 97, 32000, SupportedMediaType.AUDIO, SessionType.REGULAR, false),
		H264("H264", 96, 90000, SupportedMediaType.VIDEO, SessionType.EARLY, true);

		private final String encoding;
		private final int type;
		private final int clockRate;
		private final SupportedMediaType mediaType;
		private final SessionType allowedSessionType;
		private final boolean enabledByDefault;

		AvailableMediaCodec(String encoding, int type, int clockRate, SupportedMediaType mediaType,
				SessionType allowedSessionType, boolean enabledByDefault) {
			this.encoding = encoding;
			this.type = type;
			this.clockRate = clockRate;
			this.mediaType = mediaType;
			this.allowedSessionType = allowedSessionType;
			this.enabledByDefault = enabledByDefault;
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

		public SupportedMediaType getMediaType() {
			return mediaType;
		}

		public SessionType getAllowedSessionType() {
			return allowedSessionType;
		}

		public boolean isEnabledByDefault() {
			return enabledByDefault;
		}

	}

	private class Pair<T, K> {

		public final T first;
		public final K second;

		public Pair(T first, K second) {
			this.first = first;
			this.second = second;
		}

	}

    Map<SipuadaPlugin.MediaCodecInstance, Boolean> startedSockets = new HashMap<>();
    Map<SipuadaPlugin.MediaCodecInstance, Pair<DatagramSocket, DatagramSocket>> incomingSockets = new HashMap<>();
    Map<SipuadaPlugin.MediaCodecInstance, Pair<Thread, Thread>> incomingSocketThreads = new HashMap<>();
    Map<SipuadaPlugin.MediaCodecInstance, Boolean> finishedIncomingSockets = new HashMap<>();
    Map<SipuadaPlugin.MediaCodecInstance, Pair<DatagramSocket, DatagramSocket>> outgoingSockets = new HashMap<>();
    Map<SipuadaPlugin.MediaCodecInstance, Pair<Thread, Thread>> outgoingSocketThreads = new HashMap<>();
    Map<SipuadaPlugin.MediaCodecInstance, Boolean> finishedOutgoingSockets = new HashMap<>();

	public UdpPacketsCheckerSipuadaPlugin() {
		startPlugin(UUID.randomUUID().toString(),
			UdpPacketsCheckerSipuadaPlugin.class.getSimpleName(),
			AvailableMediaCodec.class);
	}

	@Override
	protected void doStartPlugin() {}

	@Override
	protected void doStopPlugin() {}

	@Override
	protected boolean doSetupPreparedStreams(String callId, SessionType type,
			Map<String, Map<MediaCodecInstance, Session>> preparedStreams) {
		for (MediaCodecInstance supportedMediaCodec : preparedStreams.get(getSessionKey(callId, type)).keySet()) {
			Session session = preparedStreams.get(getSessionKey(callId, type)).get(supportedMediaCodec);
			doSetupStream(callId, type, session, supportedMediaCodec);
		}
		return true;
	}

	private void doSetupStream(String callId, SessionType type, Session session,
			final MediaCodecInstance supportedMediaCodec) {
		boolean streamIsRejected = false;
		switch (roles.get(getSessionKey(callId, type))) {
		case CALLER:
			if (session.getRemoteDataPort() == 0) {
				streamIsRejected = true;
			}
			break;
		case CALLEE:
			if (session.getLocalDataPort() == 0) {
				streamIsRejected = true;
			}
			break;
		}
		if (!streamIsRejected) {
			String localDataAddress = session.getLocalDataAddress();
			int localDataPort = session.getLocalDataPort();
			String remoteDataAddress = session.getRemoteDataAddress();
			int remoteDataPort = session.getRemoteDataPort();
			String localControlAddress = session.getLocalControlAddress();
			int localControlPort = session.getLocalControlPort();
			String remoteControlAddress = session.getRemoteControlAddress();
			int remoteControlPort = session.getRemoteControlPort();
			logger.info("^^ Should setup a {} *data* stream ({})"
				+ " from {}:{} (origin) to {}:{} (destination)! ^^",
				supportedMediaCodec.getEncoding(),
				supportedMediaCodec.getMediaType(),
				localDataAddress, localDataPort,
				remoteDataAddress, remoteDataPort);
			logger.info("^^ Should setup a {} *control* stream ({})"
				+ " from {}:{} (origin) to {}:{} (destination)! ^^",
				supportedMediaCodec.getEncoding(),
				supportedMediaCodec.getMediaType(),
				localControlAddress, localControlPort,
				remoteControlAddress, remoteControlPort);
	        logger.debug("@@ StartedSocketsMap: {} @@", startedSockets);
	        if (startedSockets.containsKey(supportedMediaCodec)
	                && startedSockets.get(supportedMediaCodec)) {
	            logger.debug("@@ onStreamSetupEvent: {}/{} (IGNORED) @@",
            		supportedMediaCodec, supportedMediaCodec.getMediaType());
	            return;
	        } else {
	            logger.debug("@@ onStreamSetupEvent: {}/{} @@",
            		supportedMediaCodec, supportedMediaCodec.getMediaType());
	            startedSockets.put(supportedMediaCodec, true);
	        }
	        DatagramSocket incomingDataSocket = null;
	        try {
	            incomingDataSocket = new DatagramSocket
	                (localDataPort, InetAddress.getByName(localDataAddress));
	            logger.debug("@@ Incoming *data* socket opened. @@");
	        } catch (UnknownHostException | SocketException anyException) {
	            logger.debug("@@ Could not create or bind incoming "
            		+ "*data* UDP socket ({}:{}). @@", localDataAddress,
            		localDataPort, anyException);
	        }
	        DatagramSocket incomingControlSocket = null;
	        try {
	            incomingControlSocket = new DatagramSocket
	                (localControlPort, InetAddress.getByName(localControlAddress));
	            logger.debug("@@ Incoming *control* socket opened. @@");
	        } catch (UnknownHostException | SocketException anyException) {
	            logger.debug("@@ Could not create or bind incoming "
            		+ "*control* UDP socket ({}:{}). @@", localControlAddress,
            		localControlPort, anyException);
	        }
	        if (incomingDataSocket != null && incomingControlSocket != null) {
	            incomingSockets.put(supportedMediaCodec,
	                new Pair<>(incomingDataSocket, incomingControlSocket));
	            startReceivingPackets(incomingDataSocket, true, supportedMediaCodec);
	            startReceivingPackets(incomingControlSocket, false, supportedMediaCodec);
	        }
	        DatagramSocket outgoingDataSocket = null;
	        try {
	            outgoingDataSocket = new DatagramSocket(null);
	            outgoingDataSocket.connect(InetAddress.getByName
            		(remoteDataAddress), remoteDataPort);
	            logger.debug("@@ Outgoing *data* socket opened. @@");
	        } catch (UnknownHostException | SocketException anyException) {
	            logger.debug("@@ Could not create or bind outgoing "
            		+ "*data* UDP socket ({}:{}). @@", remoteDataAddress,
            		remoteDataPort, anyException);
	        }

	        DatagramSocket outgoingControlSocket = null;
	        try {
	            outgoingControlSocket = new DatagramSocket(null);
	            outgoingControlSocket.connect(InetAddress.getByName
            		(remoteControlAddress), remoteControlPort);
	            logger.debug("@@ Outgoing *control* socket opened. @@");
	        } catch (UnknownHostException | SocketException anyException) {
	            logger.debug("@@ Could not create or bind outgoing "
            		+ "*control* UDP socket ({}:{}). @@", remoteControlAddress,
            		remoteControlPort, anyException);
	        }
	        if (outgoingDataSocket != null && outgoingControlSocket != null) {
	            outgoingSockets.put(supportedMediaCodec,
	                new Pair<>(outgoingDataSocket, outgoingControlSocket));
	            startSendingPackets(outgoingDataSocket, true, supportedMediaCodec);
	            startSendingPackets(outgoingControlSocket, false, supportedMediaCodec);
	        }
		}
	}

	private void startReceivingPackets(final DatagramSocket incomingSocket, final boolean isData,
			final SipuadaPlugin.MediaCodecInstance supportedMediaCodec) {
		finishedIncomingSockets.put(supportedMediaCodec, false);
		// noinspection InfiniteRecursion
		Thread socketThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					byte[] buffer = new byte[256];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					try {
						incomingSocket.receive(packet);
						logger.debug("@@ Incoming {} socket bound to {}:{} received data: {}. @@",
							(isData ? "*data*" : "*control*"), incomingSocket.getLocalAddress(),
							incomingSocket.getLocalPort(), Arrays.toString(packet.getData()));
						if (finishedIncomingSockets.containsKey(supportedMediaCodec)
								&& finishedIncomingSockets.get(supportedMediaCodec)) {
							throw new RuntimeException("@@ Incoming "
								+ (isData ? "*data*" : "*control*")
								+ " socket thread shall finish "
								+ "because socket closed. @@");
						}
					} catch (Throwable ignore) {
						ignore.printStackTrace();
						break;
					}
				}
				logger.debug("@@ Incoming {} socket thread stopped. @@",
					(isData ? "*data*" : "*control*"));
			}

		});
		if (incomingSocketThreads.get(supportedMediaCodec) == null) {
			incomingSocketThreads.put(supportedMediaCodec,
				new Pair<Thread, Thread>(null, null));
		}
		if ((isData && incomingSocketThreads.get(supportedMediaCodec).first != null)
				|| (!isData && incomingSocketThreads.get(supportedMediaCodec).second != null)) {
			if (isData) {
				logger.debug("@@ Another existing incoming *data*"
					+ " socket thread has to be interrupted. @@");
				incomingSocketThreads.get(supportedMediaCodec).first.interrupt();
			} else {
				logger.debug("@@ Another existing incoming *control*"
					+ " socket thread has to be interrupted. @@");
				incomingSocketThreads.get(supportedMediaCodec).second.interrupt();
			}
		}
		if (isData) {
			incomingSocketThreads.put(supportedMediaCodec, new Pair<>
				(socketThread, incomingSocketThreads.get(supportedMediaCodec).second));
		} else {
			incomingSocketThreads.put(supportedMediaCodec, new Pair<>
				(incomingSocketThreads.get(supportedMediaCodec).first, socketThread));
		}
		socketThread.start();
		logger.debug("@@ Incoming {} socket thread started. @@",
			(isData ? "*data*" : "*control*"));
	}

	private void startSendingPackets(final DatagramSocket outgoingSocket, final boolean isData,
			final SipuadaPlugin.MediaCodecInstance supportedMediaCodec) {
		finishedOutgoingSockets.put(supportedMediaCodec, false);
		// noinspection InfiniteRecursion
		Thread socketThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					byte[] buffer = String.format("*%s/%s*_%s:%s:%s", (isData ? "data" : "control"),
						supportedMediaCodec, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
						UUID.randomUUID().toString()).getBytes();
					if (buffer.length > 256) {
						buffer = Arrays.copyOf(buffer, 256);
					}
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					try {
						outgoingSocket.send(packet);
						logger.debug("@@ Outgoing {} socket targeting {}:{} sent data: {}. @@",
							(isData ? "*data*" : "*control*"), outgoingSocket.getInetAddress(),
							outgoingSocket.getPort(), Arrays.toString(packet.getData()));
						Thread.sleep(1500);
						if (finishedOutgoingSockets.containsKey(supportedMediaCodec)
								&& finishedOutgoingSockets.get(supportedMediaCodec)) {
							throw new RuntimeException("@@ Outgoing "
								+ (isData ? "*data*" : "*control*")
								+ " socket thread shall finish "
								+ "because socket closed. @@");
						}
					} catch (Throwable ignore) {
						ignore.printStackTrace();
						break;
					}
				}
				logger.debug("@@ Outgoing {} socket thread stopped. @@",
					(isData ? "*data*" : "*control*"));
			}

		});
		if (outgoingSocketThreads.get(supportedMediaCodec) == null) {
			outgoingSocketThreads.put(supportedMediaCodec,
				new Pair<Thread, Thread>(null, null));
		}
		if ((isData && outgoingSocketThreads.get(supportedMediaCodec).first != null)
				|| (!isData && outgoingSocketThreads.get(supportedMediaCodec).second != null)) {
			if (isData) {
				logger.debug("@@ Another existing outgoing *data* "
					+ "socket thread has to be interrupted. @@");
				outgoingSocketThreads.get(supportedMediaCodec).first.interrupt();
			} else {
				logger.debug("@@ Another existing outgoing *control* "
					+ "socket thread has to be interrupted. @@");
				outgoingSocketThreads.get(supportedMediaCodec).second.interrupt();
			}
		}
		if (isData) {
			outgoingSocketThreads.put(supportedMediaCodec, new Pair<>
				(socketThread, outgoingSocketThreads.get(supportedMediaCodec).second));
		} else {
			outgoingSocketThreads.put(supportedMediaCodec, new Pair<>
				(outgoingSocketThreads.get(supportedMediaCodec).first, socketThread));
		}
		socketThread.start();
		logger.debug("@@ Outgoing {} socket thread started. @@",
			(isData ? "*data*" : "*control*"));
	}

	@Override
	protected boolean doTerminateStreams(String callId, SessionType type,
			Map<String, Map<MediaCodecInstance, Session>> ongoingStreams) {
		for (MediaCodecInstance supportedMediaCodec : preparedStreams.get(getSessionKey(callId, type)).keySet()) {
			Session session = preparedStreams.get(getSessionKey(callId, type)).get(supportedMediaCodec);
			doTerminateStream(session, supportedMediaCodec);
		}
		return true;
	}

    private void doTerminateStream(Session session, MediaCodecInstance supportedMediaCodec) {
        logger.info("^^ Should terminate {} *data* stream ({}) from "
            + "{}:{} (origin) to {}:{} (destination)! ^^",
            supportedMediaCodec.getEncoding(), supportedMediaCodec.getMediaType(),
            session.getLocalDataAddress(), session.getLocalDataPort(),
            session.getRemoteDataAddress(), session.getRemoteDataPort());
        logger.info("^^ Should terminate {} *control* stream ({}) from "
            + "{}:{} (origin) to {}:{} (destination)! ^^",
            supportedMediaCodec.getEncoding(), supportedMediaCodec.getMediaType(),
            session.getLocalControlAddress(), session.getLocalControlPort(),
            session.getRemoteControlAddress(), session.getRemoteControlPort());
        logger.debug("@@ StartedSocketsMap: {} @@", startedSockets);
        if (!startedSockets.containsKey(supportedMediaCodec)
                || !startedSockets.get(supportedMediaCodec)) {
        	logger.debug("@@ onStreamTerminationEvent: {} (IGNORED) @@",
    			supportedMediaCodec.getMediaType());
            return;
        } else {
            logger.debug("@@ onStreamTerminationEvent: {} @@",
        		supportedMediaCodec.getMediaType());
            startedSockets.put(supportedMediaCodec, false);
        }
        Pair<DatagramSocket, DatagramSocket> incomingDataAndControlSockets
            = incomingSockets.remove(supportedMediaCodec);
        if (incomingDataAndControlSockets != null) {
            finishedIncomingSockets.put(supportedMediaCodec, true);
            incomingDataAndControlSockets.first.close();
            logger.debug("@@ Incoming *data* socket closed. @@");
            incomingDataAndControlSockets.second.close();
            logger.debug("@@ Incoming *control* socket closed. @@");
        }
        if (incomingSocketThreads.get(supportedMediaCodec) == null) {
            incomingSocketThreads.put(supportedMediaCodec,
        		new Pair<Thread, Thread>(null, null));
        }
        if (incomingSocketThreads.get(supportedMediaCodec).first != null) {
        	logger.debug("@@ Interrupting incoming *data* socket thread... @@");
            incomingSocketThreads.get(supportedMediaCodec).first.interrupt();
            incomingSocketThreads.put(supportedMediaCodec, new Pair<Thread, Thread>
                (null, incomingSocketThreads.get(supportedMediaCodec).second));
        }
        if (incomingSocketThreads.get(supportedMediaCodec).second != null) {
        	logger.debug("@@ Interrupting incoming *control* socket thread... @@");
            incomingSocketThreads.get(supportedMediaCodec).second.interrupt();
            incomingSocketThreads.put(supportedMediaCodec, new Pair<Thread, Thread>
                (incomingSocketThreads.get(supportedMediaCodec).first, null));
        }
        Pair<DatagramSocket, DatagramSocket> outgoingDataAndControlSockets
            = outgoingSockets.remove(supportedMediaCodec);
        if (outgoingDataAndControlSockets != null) {
            finishedOutgoingSockets.put(supportedMediaCodec, true);
            outgoingDataAndControlSockets.first.close();
            logger.debug("@@ Outgoing *data* socket closed. @@");
            outgoingDataAndControlSockets.second.close();
            logger.debug("@@ Outgoing *control* socket closed. @@");
        }
        if (outgoingSocketThreads.get(supportedMediaCodec) == null) {
            outgoingSocketThreads.put(supportedMediaCodec,
        		new Pair<Thread, Thread>(null, null));
        }
        if (outgoingSocketThreads.get(supportedMediaCodec).first != null) {
        	logger.debug("@@ Interrupting outgoing *data* socket thread... @@");
            outgoingSocketThreads.get(supportedMediaCodec).first.interrupt();
            outgoingSocketThreads.put(supportedMediaCodec, new Pair<Thread, Thread>
                (null, outgoingSocketThreads.get(supportedMediaCodec).second));
        }
        if (outgoingSocketThreads.get(supportedMediaCodec).second != null) {
        	logger.debug("@@ Interrupting outgoing *control* socket thread... @@");
            outgoingSocketThreads.get(supportedMediaCodec).second.interrupt();
            outgoingSocketThreads.put(supportedMediaCodec, new Pair<Thread, Thread>
                (outgoingSocketThreads.get(supportedMediaCodec).first, null));
        }
    }

}
