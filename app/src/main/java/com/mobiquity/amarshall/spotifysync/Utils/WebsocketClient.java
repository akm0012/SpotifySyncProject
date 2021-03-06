package com.mobiquity.amarshall.spotifysync.Utils;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.mobiquity.amarshall.spotifysync.Interfaces.WebSocketListener;
import com.mobiquity.amarshall.spotifysync.Models.SpoqModel;
import com.mobiquity.amarshall.spotifysync.Models.SpoqTrack;
import com.mobiquity.amarshall.spotifysync.Models.SpoqUser;

import org.glassfish.tyrus.client.ClientManager;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

public class WebsocketClient {
    private static CountDownLatch messageLatch;
    private static WebsocketClient client;
    private Session currentSession;
    private SpoqModel spoqModel;

    private WebsocketClient() {

    }

    public static WebsocketClient getInstance() {
        if (client == null) {
            client = new WebsocketClient();
        }
        return client;
    }

    public boolean isOpen() {
        return currentSession != null && currentSession.isOpen();
    }

    public SocketAsyncTask startConnection(WebSocketListener listener) {
        return (SocketAsyncTask) new SocketAsyncTask(listener).execute();
    }

    public Runnable addTrack(SpoqTrack track) {
        Gson gson = new Gson();
        final String json = TrackQueueCommands.ADD_SONG + gson.toJson(track);
        return getCommandRunnable(json);
    }

    public Runnable joinPlaylist(SpoqUser user) {
        Gson gson = new Gson();
        final String json = TrackQueueCommands.JOIN_PLAYLIST + gson.toJson(user);
        return getCommandRunnable(json);
    }

    public Runnable createPlaylist(SpoqUser user) {
        Gson gson = new Gson();
        final String json = TrackQueueCommands.CREATE_PLAYLIST + gson.toJson(user);
        return getCommandRunnable(json);
    }

    public Runnable leavePlaylist() {
        final String json = TrackQueueCommands.LEAVE_PLAYLIST;
        return getCommandRunnable(json);
    }

    public Runnable downVoteTrack(SpoqTrack track) {
        Gson gson = new Gson();
        final String json = TrackQueueCommands.DOWN_VOTE + gson.toJson(track);
        return getCommandRunnable(json);
    }

    public Runnable removeDownVote(SpoqTrack track) {
        Gson gson = new Gson();
        final String json = TrackQueueCommands.REMOVE_DOWN_VOTE + gson.toJson(track);
        return getCommandRunnable(json);
    }

    public Runnable getCommandRunnable(final String json) {
        if (currentSession == null) {
            return null;
        }

        return new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i("Doc", "Sending: " + json);
                    currentSession.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    public static class TrackQueueCommands {
        public static final String CREATE_PLAYLIST = "create_playlist!";
        public static final String JOIN_PLAYLIST = "join_playlist!";
        public static final String LEAVE_PLAYLIST = "leave_playlist!";
        public static final String ADD_SONG = "add_song!";
        public static final String DOWN_VOTE = "down_vote!";
        public static final String REMOVE_DOWN_VOTE = "remove_down_vote!";
        public static final String ERROR = "error!";
    }

    class SocketAsyncTask extends AsyncTask<Void, Void, Void> {
        private WebSocketListener listener;

        public SocketAsyncTask(WebSocketListener listener) {
            this.listener = listener;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                messageLatch = new CountDownLatch(1);

                final ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
                URI uri = new URI(Constants.ADDRESS + ":" + Constants.PORT + Constants.END_ADDRESS);

                ClientManager manager = ClientManager.createClient();
                currentSession = manager.connectToServer(new Endpoint() {
                    @Override
                    public void onOpen(Session session, EndpointConfig config) {
                        currentSession = session;
                        session.addMessageHandler(new MessageHandler.Whole<String>() {
                            @Override
                            public void onMessage(String message) {
                                Log.i("Doc", "Receiving: " + message);
                                Gson gson = new Gson();
                                if (message.equalsIgnoreCase(TrackQueueCommands.ERROR)) {
                                    Log.i("Doc", "error");
                                    listener.onFailure(message);
                                } else {
                                    Log.i("Doc", message);
                                    spoqModel = gson.fromJson(message, SpoqModel.class);
                                    listener.onSuccess(spoqModel);
                                }
                                messageLatch.countDown();
                            }
                        });
                        listener.onConnectionOpen();
                    }

                    @Override
                    public void onClose(Session session, CloseReason closeReason) {
                        currentSession = null;
                        super.onClose(session, closeReason);
                        listener.onConnectionClosed();
                    }
                }, cec, uri);
                messageLatch.await(100, TimeUnit.SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }
}