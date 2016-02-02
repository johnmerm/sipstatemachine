package org.igor.rtc.sipstatemachine.sip.media;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.sdp.Connection;
import javax.sdp.Media;
import javax.sdp.MediaDescription;
import javax.sdp.Origin;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;
import javax.sdp.SessionName;

import org.igor.rtc.sipstatemachine.sip.SIPHandler;

import gov.nist.javax.sdp.fields.AttributeField;

public class UDPEchoMediaHandler implements MediaHandler{
	private SdpFactory sdpFactory = SdpFactory.getInstance();
	
	private Map<Long,EchoThread> map = new ConcurrentHashMap<>();
	
	@SuppressWarnings({"unchecked" })
	@Override
	public SessionDescription answer(SessionDescription offer) throws SdpException{
		SessionDescription answer = sdpFactory.createSessionDescription();
		Vector<MediaDescription> offeredMedia = offer.getMediaDescriptions(false);
		
		Connection answerConnection = createConnection(offer.getConnection());
		long sessionId = (long)(Math.random()*10000);
		Origin origin = sdpFactory.createOrigin(SIPHandler.userAgent, sessionId, 1, answerConnection.getNetworkType(), answerConnection.getAddressType(), answerConnection.getAddress());
		answer.setOrigin(origin);
		Vector<MediaDescription> answerMedia = handleMedia(sessionId,offer.getConnection(),offeredMedia);
		
		
		
		
		SessionName sessionName = sdpFactory.createSessionName(SIPHandler.userAgent);
		answer.setSessionName(sessionName);
		
		answer.setConnection(answerConnection);
		answer.setMediaDescriptions(answerMedia);
		return answer;
	}
	
	class EchoThread extends Thread{
		private SocketAddress remoteSocketAddress;
		private DatagramSocket socket;

		public EchoThread(SocketAddress remoteSocketAddress,DatagramSocket socket) {
			super();
			this.remoteSocketAddress = remoteSocketAddress;
			this.socket = socket;
		}
		@Override
		public void run() {
			
			byte[] buff = new byte[4096];
			DatagramPacket packet = new DatagramPacket(buff, 4096);
			try {
				socket.connect(remoteSocketAddress);
				while(true){
					socket.receive(packet);
					socket.send(packet);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	protected Connection createConnection(Connection offeredConenction) throws SdpException{
		//TODO: myIp should connect to other IP;
		try {
			String myIp = InetAddress.getLocalHost().getHostAddress();
			Connection myConnection = sdpFactory.createConnection(offeredConenction.getNetworkType(), offeredConenction.getAddressType(), myIp);
			return myConnection;
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	protected Vector<MediaDescription> handleMedia(long sessionId,Connection offeredConenction,Vector<MediaDescription> offeredMedia) throws SdpParseException{
		String oa = offeredConenction.getAddress();
		Vector<MediaDescription> media = offeredMedia.stream().map(md -> {
			try {
				int remotePort = md.getMedia().getMediaPort();
				SocketAddress remoteSocketAddress = new InetSocketAddress(oa, remotePort);
				DatagramSocket socket = new DatagramSocket();
				EchoThread thread = new EchoThread(remoteSocketAddress,socket);
				map.put(sessionId, thread);
				
				Media om = md.getMedia();
				
				Vector<String> mediaFormats = om.getMediaFormats(false);
				
				int formats[] = mediaFormats.stream().mapToInt(Integer::parseInt).toArray();
				MediaDescription answerMD = sdpFactory.createMediaDescription(om.getMediaType(), socket.getLocalPort(), om.getPortCount(), om.getProtocol(), formats);
				
				Vector<AttributeField> attributes =md.getAttributes(false); 
				attributes.forEach(a->{
					answerMD.addAttribute(a);
				});
				return answerMD;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		
			
		}).collect(Collectors.toCollection(Vector::new));
		
		return media;
	}
	@Override
	public void start(long sessionId){
		EchoThread thread = map.get(sessionId);
		if (thread !=null){
			thread.start();
		}
	}

}
