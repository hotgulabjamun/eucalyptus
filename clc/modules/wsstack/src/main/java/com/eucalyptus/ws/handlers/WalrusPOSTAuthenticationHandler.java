/*******************************************************************************
 *Copyright (c) 2009  Eucalyptus Systems, Inc.
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, only version 3 of the License.
 * 
 * 
 *  This file is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 * 
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Please contact Eucalyptus Systems, Inc., 130 Castilian
 *  Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 *  if you need additional information or have any questions.
 * 
 *  This file may incorporate work covered under the following copyright and
 *  permission notice:
 * 
 *    Software License Agreement (BSD License)
 * 
 *    Copyright (c) 2008, Regents of the University of California
 *    All rights reserved.
 * 
 *    Redistribution and use of this software in source and binary forms, with
 *    or without modification, are permitted provided that the following
 *    conditions are met:
 * 
 *      Redistributions of source code must retain the above copyright notice,
 *      this list of conditions and the following disclaimer.
 * 
 *      Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 * 
 *    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *    IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 *    TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *    PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 *    OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *    EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *    PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *    PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *    LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *    NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 *    THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 *    LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 *    SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *    IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 *    BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 *    THE REGENTS’ DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 *    OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 *    WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 *    ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************/
package com.eucalyptus.ws.handlers;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;

import org.apache.log4j.Logger;
import org.apache.tools.ant.util.DateUtils;
import org.bouncycastle.util.encoders.Base64;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import com.eucalyptus.auth.Hashes;
import com.eucalyptus.auth.NoSuchUserException;
import com.eucalyptus.auth.User;
import com.eucalyptus.auth.CredentialProvider;
import com.eucalyptus.util.WalrusProperties;
import com.eucalyptus.ws.AuthenticationException;
import com.eucalyptus.ws.MappingHttpRequest;

@ChannelPipelineCoverage("one")
public class WalrusPOSTAuthenticationHandler extends MessageStackHandler {
	private static Logger LOG = Logger.getLogger( WalrusPOSTAuthenticationHandler.class );
	private final static long EXPIRATION_LIMIT = 900000;
	private String boundary;

	public enum SecurityParameter {
		AWSAccessKeyId,
		Timestamp,
		Expires,
		Signature,
		Authorization,
		Date,
		Content_MD5,
		Content_Type
	}

	@Override
	public void handleUpstream( final ChannelHandlerContext channelHandlerContext, final ChannelEvent channelEvent ) throws Exception {
		LOG.debug( this.getClass( ).getSimpleName( ) + "[incoming]: " + channelEvent );
		if ( channelEvent instanceof MessageEvent ) {
			final MessageEvent msgEvent = ( MessageEvent ) channelEvent;
			this.incomingMessage( channelHandlerContext, msgEvent );
		} else if ( channelEvent instanceof ExceptionEvent ) {
			this.exceptionCaught( channelHandlerContext, ( ExceptionEvent ) channelEvent );
		}
		channelHandlerContext.sendUpstream( channelEvent );
	}

	@Override
	public void incomingMessage( ChannelHandlerContext ctx, MessageEvent event ) throws Exception {
		if ( event.getMessage( ) instanceof MappingHttpRequest ) {
			MappingHttpRequest httpRequest = ( MappingHttpRequest ) event.getMessage( );
			Map formFields = httpRequest.getFormFields();
			String boundary = processPOSTHeaders(httpRequest, formFields);
			processPOSTParams(boundary, formFields, httpRequest.getContent());
			checkPolicy(httpRequest, formFields);
			handle(httpRequest);
		} else if(event.getMessage() instanceof DefaultHttpChunk) {
			DefaultHttpChunk httpChunk = (DefaultHttpChunk) event.getMessage();
			//if(httpChunk.isLast()) {
			ChannelBuffer newBuffer = getDataChunk(httpChunk.getContent(), boundary);
			if(newBuffer != null) {
				DefaultHttpChunk newChunk = new DefaultHttpChunk(newBuffer);
				UpstreamMessageEvent newEvent = new UpstreamMessageEvent(ctx.getChannel(), newChunk, null);
				ctx.sendUpstream(newEvent);
			}
		}
	}

	public void handle(MappingHttpRequest httpRequest) throws AuthenticationException
	{
		if(httpRequest.getFormFields().size() > 0) {
			String data = httpRequest.getAndRemoveHeader(WalrusProperties.FormField.FormUploadPolicyData.toString());
			String auth_part = httpRequest.getAndRemoveHeader(SecurityParameter.Authorization.toString());
			try {
				User user = CredentialProvider.getUser( "admin" );
				user.setIsAdministrator(true);
				httpRequest.setUser( user );
			} catch (NoSuchUserException e) {
				throw new AuthenticationException( "User authentication failed." );
			}  
/*	if(auth_part != null) {
				String sigString[] = getSigInfo(auth_part);
				String signature = sigString[1];				
				authenticate(httpRequest, sigString[0], signature, data);
			} else {
				throw new AuthenticationException("User authentication failed.");
			}*/
		} else {
			//anonymous request              			
		}
	}

	private void authenticate(MappingHttpRequest httpRequest, String accessKeyID, String signature, String data) throws AuthenticationException {
		signature = signature.replaceAll("=", "");
		try {
			String queryKey = CredentialProvider.getSecretKey(accessKeyID);
			String authSig = checkSignature( queryKey, data );
			if (!authSig.equals(signature))
				throw new AuthenticationException( "User authentication failed. Could not verify signature" );
			String userName = CredentialProvider.getUserName( accessKeyID );
			User user = CredentialProvider.getUser(userName);  
			httpRequest.setUser( user );
		} catch(Exception ex) {
			throw new AuthenticationException( "User authentication failed. Unable to obtain query key" );
		}
	}

	private String[] getSigInfo (String auth_part) {
		int index = auth_part.lastIndexOf(" ");
		String sigString = auth_part.substring(index + 1);
		return sigString.split(":");
	}

	protected String checkSignature( final String queryKey, final String subject ) throws AuthenticationException
	{
		SecretKeySpec signingKey = new SecretKeySpec( queryKey.getBytes(), Hashes.Mac.HmacSHA1.toString() );
		try
		{
			Mac mac = Mac.getInstance( Hashes.Mac.HmacSHA1.toString() );
			mac.init( signingKey );
			byte[] rawHmac = mac.doFinal( subject.getBytes() );
			return new String(Base64.encode( rawHmac )).replaceAll( "=", "" );
		}
		catch ( Exception e )
		{
			LOG.error( e, e );
			throw new AuthenticationException( "Failed to compute signature" );
		}
	}

	private String processPOSTHeaders(MappingHttpRequest httpRequest, Map<String, String> formFields) throws AuthenticationException {
		String contentType = httpRequest.getHeader(WalrusProperties.CONTENT_TYPE);
		String boundary = null;
		if(contentType != null) {
			if(contentType.startsWith(WalrusProperties.MULTIFORM_DATA_TYPE)) {
				boundary = getFormFieldKeyName(contentType, "boundary");
				boundary = "--" + boundary + "\r\n";
				this.boundary = boundary;
			}
			String[] target = getTarget(httpRequest);
			formFields.put(WalrusProperties.FormField.bucket.toString(), target[0]);
			return boundary;
		} else {
			throw new AuthenticationException("No Content-Type specified");
		}
	}

	private void processPOSTParams(String boundary, Map formFields, ChannelBuffer buffer) throws AuthenticationException {
		String message = getMessageString(buffer);
		String[] parts = message.split(boundary);
		for(String part : parts) {
			Map<String, String> keyMap = getFormField(part, "name");
			Set<String> keys = keyMap.keySet();
			for(String key : keys) {
				if(WalrusProperties.FormField.file.toString().equals(key)) {
					String contentType = getContentType(formFields, part, boundary);
					if(contentType != null) {
						getFirstChunk(formFields, buffer, contentType, boundary);
					}
				}
				formFields.put(key, keyMap.get(key));
			}
		}
	}

	private void checkPolicy(MappingHttpRequest httpRequest, Map<String, String> formFields) throws AuthenticationException {
		if(formFields.containsKey(WalrusProperties.FormField.policy.toString())) {
			String authenticationHeader = "";
			String policy = new String(Base64.decode(formFields.remove(WalrusProperties.FormField.policy.toString())));
			String policyData;
			try {
				policyData = new String(Base64.encode(policy.getBytes()));
			} catch (Exception ex) {
				LOG.warn(ex, ex);
				throw new AuthenticationException("error reading policy data.");
			}
			//parse policy
			try {
				JsonSlurper jsonSlurper = new JsonSlurper();
				JSONObject policyObject = (JSONObject)jsonSlurper.parseText(policy);
				String expiration = (String) policyObject.get(WalrusProperties.PolicyHeaders.expiration.toString());
				if(expiration != null) {
					Date expirationDate = DateUtils.parseIso8601DateTimeOrDate(expiration);
					if((new Date()).getTime() > expirationDate.getTime()) {
						LOG.warn("Policy has expired.");
						//throw new AuthenticationException("Policy has expired.");
					}
				}
				List<String> policyItemNames = new ArrayList<String>();

				JSONArray conditions = (JSONArray) policyObject.get(WalrusProperties.PolicyHeaders.conditions.toString());
				for (int i = 0 ; i < conditions.size() ; ++i) {
					Object policyItem = conditions.get(i);
					if(policyItem instanceof JSONObject) {
						JSONObject jsonObject = (JSONObject) policyItem;
						if(!exactMatch(jsonObject, formFields, policyItemNames)) {
							LOG.warn("Policy verification failed. ");
							//throw new AuthenticationException("Policy verification failed.");
						}
					} else if(policyItem instanceof  JSONArray) {
						JSONArray jsonArray = (JSONArray) policyItem;
						if(!partialMatch(jsonArray, formFields, policyItemNames)) {
							LOG.warn("Policy verification failed. ");
							//throw new AuthenticationException("Policy verification failed.");
						}
					}
				}

				Set<String> formFieldsKeys = formFields.keySet();
				for(String formKey : formFieldsKeys) {
					if(formKey.startsWith(WalrusProperties.IGNORE_PREFIX))
						continue;
					boolean fieldOkay = false;
					for(WalrusProperties.IgnoredFields field : WalrusProperties.IgnoredFields.values()) {
						if(formKey.equals(field.toString())) {
							fieldOkay = true;
							break;
						}
					}
					if(fieldOkay)
						continue;
					if(policyItemNames.contains(formKey))
						continue;
					LOG.warn("All fields except those marked with x-ignore- should be in policy.");
					//throw new AuthenticationException("All fields except those marked with x-ignore- should be in policy.");
				}
			} catch(Exception ex) {
				//rethrow
				LOG.warn(ex);
				if(ex instanceof AuthenticationException)
					throw (AuthenticationException)ex;
			}
			//all form uploads without a policy are anonymous
			if(formFields.containsKey(WalrusProperties.FormField.AWSAccessKeyId.toString())) {
				String accessKeyId = formFields.remove(WalrusProperties.FormField.AWSAccessKeyId.toString());
				authenticationHeader += "AWS" + " " + accessKeyId + ":";
			}
			if(formFields.containsKey(WalrusProperties.FormField.signature.toString())) {
				String signature = formFields.remove(WalrusProperties.FormField.signature.toString());
				authenticationHeader += signature;
				httpRequest.addHeader(WalrusPOSTAuthenticationHandler.SecurityParameter.Authorization.toString(), authenticationHeader);
			}
			httpRequest.addHeader(WalrusProperties.FormField.FormUploadPolicyData.toString(), policyData);
		}
	}

	private Map<String, String> getFormField(String message, String key) {
		Map<String, String> keymap = new HashMap<String, String>();
		String[] parts = message.split(";");
		if(parts.length >= 2) {
			if (parts[1].contains(key + "=")) {
				String keystring = parts[1].substring(parts[1].indexOf('=') + 1);
				if(parts.length == 2) {
					String[] keyparts = keystring.split("\r\n\r\n");
					String keyName = keyparts[0];
					keyName = keyName.replaceAll("\"", "");
					String value = keyparts[1].replaceAll("\r\n", "");
					keymap.put(keyName, value);
				} else {
					String keyName = keystring.trim();
					keyName = keyName.replaceAll("\"", "");
					String valuestring = parts[2].substring(parts[2].indexOf('=') + 1, parts[2].indexOf("\r\n")).trim();
					String value = valuestring.replaceAll("\"", "");
					keymap.put(keyName, value);
				}
			}
		}
		return keymap;		
	}

	private String getFormFieldKeyName(String message, String key) {
		String[] parts = message.split(";");
		if(parts.length > 1) {
			if (parts[1].contains(key + "=")) {
				String keystring = parts[1].substring(parts[1].indexOf('=') + 1);
				String[] keyparts = keystring.split("\r\n\r\n");
				String keyName = keyparts[0];
				keyName = keyName.replaceAll("\r\n", "");
				keyName = keyName.replaceAll("\"", "");
				return keyName;
			}
		}
		return null;		
	}

	private String getMessageString(ChannelBuffer buffer) {
		buffer.markReaderIndex( );
		byte[] read = new byte[buffer.readableBytes( )];
		buffer.readBytes( read );
		buffer.resetReaderIndex();
		return new String( read );
	}

	private String getContentType(Map<String, String>formFields, String part, String boundary) {
		int endValue = part.indexOf("\r\n\r\n");
		int startValue = part.indexOf(WalrusProperties.CONTENT_TYPE + ":") + WalrusProperties.CONTENT_TYPE.length() + 1;
		if(endValue > startValue) {
			String contentType = part.substring(startValue, endValue);
			formFields.put(WalrusProperties.CONTENT_TYPE, contentType);
			return contentType;
			//formFields.put(WalrusProperties.IGNORE_PREFIX + "FirstDataChunk", firstChunk);
		}
		return null;
	}

	private void getFirstChunk(Map formFields, ChannelBuffer buffer, String contentType, String boundary) {
		buffer.markReaderIndex();
		String contentTypeString = WalrusProperties.CONTENT_TYPE.toString() + ":" + contentType + "\r\n\r\n";
		byte[] read = new byte[buffer.readableBytes( )];
		buffer.readBytes( read );
		int index = getLastIndex(read, contentTypeString.getBytes());
		if(index > -1) {
			int firstIndex = index + 1;
			int lastIndex = read.length;
			boundary = "\r\n" + boundary;
			index = getFirstIndex(read, firstIndex, boundary.getBytes());
			if(index > -1) 
				lastIndex = index;
			byte[] chunk = new byte[lastIndex - firstIndex];
			ChannelBuffer firstBuffer = ChannelBuffers.copiedBuffer(read, firstIndex, (lastIndex - firstIndex));
			formFields.put(WalrusProperties.IGNORE_PREFIX + "FirstDataChunk", firstBuffer);			
		}
		buffer.resetReaderIndex();
	}

	private ChannelBuffer getDataChunk(ChannelBuffer buffer, String boundary) {
		buffer.markReaderIndex();
		byte[] read = new byte[buffer.readableBytes( )];
		buffer.readBytes( read );
		boundary = "\r\n" + boundary;
		int index = getFirstIndex(read, 0, boundary.getBytes());
		if(index > -1) {
			String readString = new String(read);
			ChannelBuffer newBuffer = ChannelBuffers.copiedBuffer(read, 0, index);
			String newstring = new String(read, 0, index);
			return newBuffer;
		}
		buffer.resetReaderIndex();
		return null;
	}

	private int getFirstIndex(byte[] bytes, int sourceIndex, byte[] bytesToCompare) {
		int firstIndex = -1;
		if((bytes.length - sourceIndex) < bytesToCompare.length)
			return firstIndex;
		for(int i=sourceIndex; i < bytes.length; ++i) {
			for(int j=0; j < bytesToCompare.length && ((i + j) < bytes.length); ++j) {
				if(bytes[i + j] == bytesToCompare[j]) {
					firstIndex = i;
				} else {
					firstIndex = -1;
					break;
				}
			}
			if(firstIndex != -1)
				return firstIndex;
		}
		return firstIndex;
	}

	private int getLastIndex(byte[] bytes, byte[] bytesToCompare) {
		int lastIndex = -1;
		if(bytes.length < bytesToCompare.length)
			return lastIndex;
		for(int i=0; i < bytes.length; ++i) {
			for(int j=0; j < bytesToCompare.length && ((i + j) < bytes.length); ++j) {
				if(bytes[i + j] == bytesToCompare[j]) {
					lastIndex = i + j;
				} else {
					lastIndex = -1;
					break;
				}
			}
			if(lastIndex != -1)
				return lastIndex;
		}
		return lastIndex;
	}

	private boolean exactMatch(JSONObject jsonObject, Map formFields, List<String> policyItemNames) {
		Iterator<String> iterator = jsonObject.keys();
		boolean returnValue = false;
		while(iterator.hasNext()) {
			String key = iterator.next();
			key = key.replaceAll("\\$", "");
			policyItemNames.add(key);
			try {
				if(jsonObject.get(key).equals(formFields.get(key)))
					returnValue = true;
				else
					returnValue = false;
			} catch(Exception ex) {
				ex.printStackTrace();
				return false;
			}
		}
		return returnValue;
	}

	private boolean partialMatch(JSONArray jsonArray, Map<String, String> formFields, List<String> policyItemNames) {
		boolean returnValue = false;
		if(jsonArray.size() != 3)
			return false;
		try {
			String condition = (String) jsonArray.get(0);
			String key = (String) jsonArray.get(1);
			key = key.replaceAll("\\$", "");
			policyItemNames.add(key);
			String value = (String) jsonArray.get(2);
			if(condition.contains("eq")) {
				if(value.equals(formFields.get(key)))
					returnValue = true;
			} else if(condition.contains("starts-with")) {
				if(!formFields.containsKey(key))
					return false;
				if(formFields.get(key).startsWith(value))
					returnValue = true;
			}
		} catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}
		return returnValue;
	}

	private static String[] getTarget(MappingHttpRequest httpRequest) {
		String operationPath = httpRequest.getServicePath().replaceAll(WalrusProperties.walrusServicePath, "");
		operationPath = operationPath.replaceAll("/{2,}", "/");
		if(operationPath.startsWith("/"))
			operationPath = operationPath.substring(1);
		return operationPath.split("/");
	}

	@Override
	public void exceptionCaught( final ChannelHandlerContext ctx, final ExceptionEvent exceptionEvent ) throws Exception {
		LOG.info("[exception " + exceptionEvent + "]");
		final HttpResponse response = new DefaultHttpResponse( HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR );
		DownstreamMessageEvent newEvent = new DownstreamMessageEvent( ctx.getChannel( ), ctx.getChannel().getCloseFuture(), response, null );
		ctx.sendDownstream( newEvent );
		newEvent.getFuture( ).addListener( ChannelFutureListener.CLOSE );
	}

	@Override
	public void outgoingMessage( ChannelHandlerContext ctx, MessageEvent event ) throws Exception {
	}
}