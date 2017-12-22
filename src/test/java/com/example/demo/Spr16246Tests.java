/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.zip.CRC32;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.ipc.netty.http.client.HttpClient;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;

import static org.junit.Assert.assertEquals;

public class Spr16246Tests {

	private MockWebServer server = new MockWebServer();

	private String url = this.server.url("/").toString();


	@Test
	public void test() throws Exception {

		MockResponse response = new MockResponse();
		response.setResponseCode(201);
		this.server.enqueue(response);

		Resource resource = new ClassPathResource("largeTextFile.txt", getClass());
		byte[] expected = Files.readAllBytes(resource.getFile().toPath());

		Flux<ByteBuf> body = getBody_ThisDoesNotWork(resource);
		// Flux<ByteBuf> body = getBody_ThisWorks(resource);

		HttpClient.create().request(HttpMethod.POST, url, req -> req.send(body).then())
				.block(Duration.ofSeconds(5));

		RecordedRequest request = this.server.takeRequest();
		ByteArrayOutputStream actual = new ByteArrayOutputStream();
		try {
			request.getBody().copyTo(actual);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
		assertEquals(expected.length, actual.size());
		assertEquals(hash(expected), hash(actual.toByteArray()));
	}

	private Flux<ByteBuf> getBody_ThisDoesNotWork(Resource resource) {
		return DataBufferUtils.read(resource, new DefaultDataBufferFactory(), 1024)
					.map(NettyDataBufferFactory::toByteBuf);
	}

	private Flux<ByteBuf> getBody_ThisWorks(Resource resource) {
		Flux<ByteBuf> body = getBody_ThisDoesNotWork(resource).cache();
		body.subscribe();
		return body;
	}

	private static long hash(byte[] bytes) {
		CRC32 crc = new CRC32();
		crc.update(bytes, 0, bytes.length);
		return crc.getValue();
	}

}
