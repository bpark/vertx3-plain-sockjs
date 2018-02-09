/*
 * Copyright 2018 Kurt Sparber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.bpark.streams;

import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import io.vertx.rxjava.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Simple Example to show plain SockJS usage without EventBus Bridge.
 *
 * Every received message is sent back to the caller, additionally the server sends each second
 * the current timestamp to all clients (fanout).
 */
public class StreamsVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(StreamsVerticle.class);

    /**
     * The handler path to connect with the sockjs server.
     */
    private static final String SOCKJS_HANDLER_PATH = "/streams/*";

    @Override
    public void start() throws Exception {

        super.start();

        Set<String> connections = new HashSet<>();

        Router router = Router.router(vertx);

        SockJSHandlerOptions options = new SockJSHandlerOptions().setInsertJSESSIONID(false);
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx, options);

        sockJSHandler.socketHandler(sockJSSocket -> {

            sockJSSocket.handler(buffer -> {

                logger.info("received message: {}", buffer.toString());

                String writeHandlerID = sockJSSocket.writeHandlerID();
                connections.add(writeHandlerID);

                logger.info("current writeHandlerId: {}", writeHandlerID);

                sockJSSocket.write(buffer);

                sockJSSocket.endHandler(nothing -> {
                    logger.info("removing writeHandlerId: {}", writeHandlerID);
                    connections.remove(writeHandlerID);
                });

            });

        });

        router.route().handler(StaticHandler.create());
        router.route(SOCKJS_HANDLER_PATH).handler(sockJSHandler);
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);

        vertx.setPeriodic(1000, id -> {
            for (String connection : connections) {
                vertx.eventBus().send(connection, io.vertx.core.buffer.Buffer.buffer("" + System.currentTimeMillis()));
            }
        });

    }
}
