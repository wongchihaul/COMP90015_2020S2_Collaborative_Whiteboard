package pb.app;

import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.IOThread;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
    private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());

    /**
     * Emitted to another peer to subscribe to updates for the given board. Argument
     * must have format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String listenBoard = "BOARD_LISTEN";

    /**
     * Emitted to another peer to unsubscribe to updates for the given board.
     * Argument must have format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String unlistenBoard = "BOARD_UNLISTEN";

    /**
     * Emitted to another peer to get the entire board data for a given board.
     * Argument must have format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String getBoardData = "GET_BOARD_DATA";

    /**
     * Emitted to another peer to give the entire board data for a given board.
     * Argument must have format "host:port:boardid%version%PATHS".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardData = "BOARD_DATA";

    /**
     * Emitted to another peer to add a path to a board managed by that peer.
     * Argument must have format "host:port:boardid%version%PATH". The numeric value
     * of version must be equal to the version of the board without the PATH added,
     * i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

    /**
     * Emitted to another peer to indicate a new path has been accepted. Argument
     * must have format "host:port:boardid%version%PATH". The numeric value of
     * version must be equal to the version of the board without the PATH added,
     * i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

    /**
     * Emitted to another peer to remove the last path on a board managed by that
     * peer. Argument must have format "host:port:boardid%version%". The numeric
     * value of version must be equal to the version of the board without the undo
     * applied, i.e. the current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

    /**
     * Emitted to another peer to indicate an undo has been accepted. Argument must
     * have format "host:port:boardid%version%". The numeric value of version must
     * be equal to the version of the board without the undo applied, i.e. the
     * current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

    /**
     * Emitted to another peer to clear a board managed by that peer. Argument must
     * have format "host:port:boardid%version%". The numeric value of version must
     * be equal to the version of the board without the clear applied, i.e. the
     * current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

    /**
     * Emitted to another peer to indicate an clear has been accepted. Argument must
     * have format "host:port:boardid%version%". The numeric value of version must
     * be equal to the version of the board without the clear applied, i.e. the
     * current version of the board.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

    /**
     * Emitted to another peer to indicate a board no longer exists and should be
     * deleted. Argument must have format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardDeleted = "BOARD_DELETED";

    /**
     * Emitted to another peer to indicate an error has occurred.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String boardError = "BOARD_ERROR";


    /**
     * White board map from board name to board object
     */
    Map<String, Whiteboard> whiteboards;

    /**
     * The currently selected white board
     */
    Whiteboard selectedBoard = null;

    /**
     * The peer:port string of the peer. This is synonomous with IP:port, host:port,
     * etc. where it may appear in comments.
     */
    String peerport = "standalone"; // a default value for the non-distributed version

    /**
     * The host of the peer.
     */
    String peerHost;

    /**
     * The server port of whiteboard server.
     */
    int whiteboardServerPort;

    /**
     * Each peer has an endpoint to connect to whiteboard server.
     */
    Endpoint indexClientEndpoint;

    /**
     * Mapping board name to a client endpoint connected to the peer that owns(i.e. shares) that board.
     */
    final Map<String, Endpoint> peerClientEndpoints;

    /**
     * Mapping board name to a set of server endpoints connected to all peers that are listening to this board.
     */
    final Map<String, Set<Endpoint>> peerServerEndpoints;


    PeerManager peerManager;
    /*
     * GUI objects, you probably don't need to modify these things... you don't
     * need to modify these things... don't modify these things [LOTR reference?].
     */

    JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
    JCheckBox sharedCheckbox;
    DrawArea drawArea;
    JComboBox<String> boardComboBox;
    boolean modifyingComboBox = false;
    boolean modifyingCheckBox = false;

    /**
     * Initialize the white board app.
     */
    public WhiteboardApp(int peerPort, String whiteboardServerHost,
                         int whiteboardServerPort) {
        whiteboards = new HashMap<>();
        peerClientEndpoints = new HashMap<>();
        peerServerEndpoints = new HashMap<>();
        this.peerport = whiteboardServerHost + ":" + peerPort;
        this.peerHost = whiteboardServerHost;
        this.whiteboardServerPort = whiteboardServerPort;
        show(peerport);
    }

    /******
     *
     * Utility methods to extract fields from argument strings.
     *
     ******/

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return peer:port:boardid
     */
    public static String getBoardName(String data) {
        String[] parts = data.split("%", 2);
        return parts[0];
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return boardid%version%PATHS
     */
    public static String getBoardIdAndData(String data) {
        String[] parts = data.split(":");
        return parts[2];
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return version%PATHS
     */
    public static String getBoardData(String data) {
        String[] parts = data.split("%", 2);
        return parts[1];
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return version
     */
    public static long getBoardVersion(String data) {
        String[] parts = data.split("%", 3);
        return Long.parseLong(parts[1]);
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return PATHS
     */
    public static String getBoardPaths(String data) {
        String[] parts = data.split("%", 3);
        return parts[2];
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return peer
     */
    public static String getIP(String data) {
        String[] parts = data.split(":");
        return parts[0];
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return port
     */
    public static int getPort(String data) {
        String[] parts = data.split(":");
        return Integer.parseInt(parts[1]);
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return peer:port:boardid%version%latestPATH
     * N.B: version is the version after path added.
     */
    public static String getLatestPath(String data) {
        String[] paths = getBoardPaths(data).split("%");
        long version = getBoardVersion(data);
        String boardIDAndVersion = getBoardName(data) + "%" + version + "%";
        return paths.length >= 1 ? boardIDAndVersion + paths[paths.length - 1] : boardIDAndVersion;
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return peer:port
     */
    public static String getPeerPort(String data) {
        String[] parts = data.split(":");
        return parts[0] + ":" + parts[1];
    }

    /**
     * @param data = hostIP:hostPort:boardid%version%PATHS
     * @return peer:port:boardid%version%PATHS
     * Use boardid as whiteboard ID and peerport as peer's ID.
     * Endpoint can use it to filter out those incoming events sent by their own.
     */
    public String myEventInfo(String data) {
        String[] parts = data.split(":", 3);
        return this.peerport + ":" + parts[2];
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return true/false
     * True if argument has same boardID but different peerport,
     * i.e., events related to same whiteboard but sent by others.
     */

    public boolean notMyRepeatedEvent(String data) {
        String boardName = getBoardName(data);
        String boardID = boardName.split(":")[2];
        String selectedID = selectedBoard.getName().split(":")[2];
        return (!getPeerPort(data).equals(this.peerport))
                && boardID.equals(selectedID);

    }


    /******
     *
     * Methods called from events.
     *
     ******/

    // From whiteboard server
    // TODO
    public void start() {
        this.peerManager = new PeerManager(getPort(peerport));
        try {
            ClientManager clientManager = peerManager.connect(whiteboardServerPort, peerHost);
            clientManager.on(PeerManager.peerStarted, args -> {
                this.indexClientEndpoint = (Endpoint) args[0];
                indexClientEndpoint.on(WhiteboardServer.sharingBoard, args2 -> {
                    if (!getPeerPort((String) args2[0]).equals(peerport)) {
                        addBoard(new Whiteboard((String) args2[0], true), false);
                    }
                }).on(WhiteboardServer.unsharingBoard, args2 -> {
                    if (!getPeerPort((String) args2[0]).equals(peerport)) {
                        deleteBoard((String) args2[0]);
                        if (peerClientEndpoints.containsKey(args2[0])) {
                            Endpoint peerClientEndpoint = peerClientEndpoints.get(args2[0]);
                            if (peerClientEndpoint != null) peerClientEndpoint.close();
                        }
                    }
                }).on(listenBoard, args2 -> {
                    // This listenBoard event that sent by peer's own is just used to trigger the collaboration.
                    // When peers change their selection of board, listenBoard event will be emitted to let peers
                    // check their up-to-date status.
                    if (getPeerPort((String) args2[0]).equals(this.peerport)) {
                        try {
                            collaborate();
                        } catch (InterruptedException | UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }

                });

            }).on(PeerManager.peerStopped, args -> {
                Endpoint endpoint = (Endpoint) args[0];
                System.out.println("Disconnected from the index server: " + endpoint.getOtherEndpointId());
            }).on(PeerManager.peerError, args -> {
                Endpoint endpoint = (Endpoint) args[0];
                log.severe("There was an error communicating with the index server: "
                        + endpoint.getOtherEndpointId());
            }).on(PeerManager.peerServerManager, args -> {
                ServerManager serverManager = (ServerManager) args[0];
                serverManager.on(IOThread.ioThread, args1 -> {
                    String port = (String) args1[0];
                    // we don't need this info, but let's log it
                    log.info("using Internet address: " + port);
                });
            });

            peerManager.start();
            clientManager.start();
            clientManager.join();
        } catch (UnknownHostException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    // From whiteboard peer
    //TODO
    public void collaborate() throws InterruptedException, UnknownHostException {
        if (selectedBoard != null) {
            if (selectedBoard.isRemote()) {
                // Editor Mode. (i.e., selectedBoard is a remote whiteboard).
                // Need to connect to the peer that owns the selected whiteboard.
                editorMode();
            } else {
                // You're the owner of selectedBoard
                ownerMode();
            }
        } else {
            log.severe("collaborating without a selected board: " + peerManager);
        }
    }


    public void editorMode() throws InterruptedException, UnknownHostException {
        String peerHost = getIP(selectedBoard.toString());
        int peerServerPort = getPort(selectedBoard.toString());
        ClientManager clientManager = peerManager.connect(peerServerPort, peerHost);
        clientManager.on(PeerManager.peerStarted, args -> {
            Endpoint endpoint = (Endpoint) args[0];
            System.out.println("Connection from peer: " + endpoint.getOtherEndpointId());

            synchronized (peerClientEndpoints) {
                peerClientEndpoints.put(selectedBoard.getName(), endpoint);
            }

            endpoint.on(boardData, args1 -> {
                if (getBoardName((String) args1[0]).equals(selectedBoard.getName())) {
                    selectedBoard.whiteboardFromString
                            (getBoardName((String) args1[0]), getBoardData((String) args1[0]));
                    selectedBoard.draw(drawArea);
                }
            }).on(boardPathUpdate, args1 -> {
                if (notMyRepeatedEvent((String) args1[0])) {
                    if (getBoardVersion((String) args1[0]) - 1 == getBoardVersion(selectedBoard.toString())) {
                        pathCreatedLocally(new WhiteboardPath(getBoardPaths((String) args1[0])));
                        selectedBoard.draw(drawArea);
                        endpoint.emit(boardPathAccepted, args1[0]);
                    } else if (getBoardVersion((String) args1[0]) < getBoardVersion(selectedBoard.toString())) {
                        //The local version is ahead of the remote version. Request for owner's whiteboard now.
                        endpoint.emit(getBoardData, selectedBoard.getName());
                    }
                }
            }).on(boardUndoUpdate, args1 -> {
                if (notMyRepeatedEvent((String) args1[0])) {
                    if (getBoardVersion((String) args1[0]) - 1 == selectedBoard.getVersion()) {
                        undoLocally();
                        selectedBoard.draw(drawArea);
                        endpoint.emit(boardUndoAccepted, args1[0]);
                    } else if (getBoardVersion((String) args1[0]) < getBoardVersion(selectedBoard.toString())) {
                        //The local version is ahead of the remote version. Request for owner's whiteboard now.
                        endpoint.emit(getBoardData, selectedBoard.getName());
                    }
                }
            }).on(boardClearUpdate, args1 -> {
                if (notMyRepeatedEvent((String) args1[0])) {
                    if (getBoardVersion((String) args1[0]) - 1 == selectedBoard.getVersion()) {
                        System.out.println("Great clear!!");
                        clearedLocally();
                        selectedBoard.draw(drawArea);
                        endpoint.emit(boardClearAccepted, args1[0]);
                    } else if (getBoardVersion((String) args1[0]) < getBoardVersion(selectedBoard.toString())) {
                        //The local version is ahead of the remote version. Request for owner's whiteboard now.
                        endpoint.emit(getBoardData, selectedBoard.getName());
                    }
                }
            }).on(boardDeleted, args1 -> {
                deleteBoard((String) args1[0]);
                clientManager.shutdown();
            }).on(boardError, args1 -> log.severe((String) args1[0])
            ).on(IOThread.ioThread, args1 -> {
                String port = (String) args1[0];
                // we don't need this info, but let's log it
                log.info("using Internet address: " + port);
            });

            //send listen and getData request to the owner of selected whiteboard.
            endpoint.emit(listenBoard, selectedBoard.getName());
            endpoint.emit(getBoardData, selectedBoard.getName());

        }).on(PeerManager.peerStopped, args -> {
            Endpoint endpoint = (Endpoint) args[0];
            System.out.println("Disconnected from the index server: " + endpoint.getOtherEndpointId());
        }).on(PeerManager.peerError, args -> {
            Endpoint endpoint = (Endpoint) args[0];
            log.severe("There was an error communicating with the index server: "
                    + endpoint.getOtherEndpointId());
        });

        clientManager.start();
    }


    public void ownerMode() {
        peerManager.on(PeerManager.peerStarted, args1 -> {

            Endpoint endpoint = (Endpoint) args1[0];
            System.out.println("Client session started: " + endpoint.getOtherEndpointId());


            endpoint.on(listenBoard, args2 -> {
                String boardRequested = (String) args2[0];
                if (whiteboards.containsKey(boardRequested)) {
                    synchronized (peerServerEndpoints) {
                        if (!peerServerEndpoints.containsKey(selectedBoard.getName())) {
                            peerServerEndpoints.put(selectedBoard.getName(), new HashSet<>());
                        }
                        Set<Endpoint> peerServerEndpointSet = peerServerEndpoints.get(selectedBoard.getName());
                        if (!peerServerEndpointSet.contains(endpoint)) {
                            peerServerEndpointSet.add(endpoint);
                            System.out.println("Peer:" + endpoint.getOtherEndpointId() + " is listening now.");
                        }
                    }
                } else {
                    endpoint.emit(boardError, "Whiteboard listened does not exist");
                }
            }).on(unlistenBoard, args2 -> {
                String boardRequested = (String) args2[0];
                if (whiteboards.containsKey(boardRequested)) {
                    synchronized (peerServerEndpoints) {
                        Set<Endpoint> peerServerEndpointSet = peerServerEndpoints.get(selectedBoard.getName());
                        if (peerServerEndpointSet.contains(endpoint)) {
                            peerServerEndpoints.get(selectedBoard.getName()).remove(endpoint);
                            System.out.println("Peer:" + endpoint.getOtherEndpointId() + " is unlistening now.");
                        }
                    }
                    endpoint.close();
                } else {
                    endpoint.emit(boardError, "Whiteboard unlistened does not exist");
                }
            }).on(getBoardData, args2 -> {
                String boardRequested = (String) args2[0];
                if (whiteboards.containsKey(boardRequested)) {
                    endpoint.emit(boardData, whiteboards.get(boardRequested).toString());
                } else {
                    endpoint.emit(boardError, "whiteboard requested does not exist");
                }
            }).on(boardPathUpdate, args2 -> {
                if (notMyRepeatedEvent((String) args2[0])) {
                    if (getBoardVersion((String) args2[0]) - 1 == getBoardVersion(selectedBoard.toString())) {
                        pathCreatedLocally(new WhiteboardPath(getBoardPaths((String) args2[0])));
                        selectedBoard.draw(drawArea);
                        endpoint.emit(boardPathAccepted, args2[0]);
                    }
                }
            }).on(boardUndoUpdate, args2 -> {
                if (notMyRepeatedEvent((String) args2[0])) {
                    if (getBoardVersion((String) args2[0]) - 1 == selectedBoard.getVersion()) {
                        undoLocally();
                        selectedBoard.draw(drawArea);
                        endpoint.emit(boardUndoAccepted, args2[0]);
                    }
                }
            }).on(boardClearUpdate, args2 -> {
                if (notMyRepeatedEvent((String) args2[0])) {
                    if (getBoardVersion((String) args2[0]) - 1 == selectedBoard.getVersion()) {
                        clearedLocally();
                        selectedBoard.draw(drawArea);
                        endpoint.emit(boardClearAccepted, args2[0]);
                    }
                }
            }).on(boardDeleted, args2 ->
                    deleteBoard((String) args2[0])
            ).on(boardError, args2 -> log.severe((String) args2[0]));

        });
    }


    /******
     *
     * Methods to manipulate data locally. Distributed systems related code has been
     * cut from these methods.
     *
     ******/

    /**
     * Wait for the peer manager to finish all threads.
     */
    public void waitToFinish() {
        peerManager.shutdown();
    }

    /**
     * Add a board to the list that the user can select from. If select is
     * true then also select this board.
     *
     * @param whiteboard
     * @param select
     */
    public void addBoard(Whiteboard whiteboard, boolean select) {
        synchronized (whiteboards) {
            whiteboards.put(whiteboard.getName(), whiteboard);
        }
        updateComboBox(select ? whiteboard.getName() : null);
    }

    /**
     * Delete a board from the list.
     *
     * @param boardname must have the form peer:port:boardid
     */
    public void deleteBoard(String boardname) {
        synchronized (whiteboards) {
            Whiteboard whiteboard = whiteboards.get(boardname);
            if (whiteboard != null) {
                whiteboards.remove(boardname);
                if (!selectedBoard.isRemote()) {
                    Set<Endpoint> peerServerEndpoint = peerServerEndpoints.get(boardname);
                    if (peerServerEndpoint != null) {
                        peerServerEndpoint.forEach(e -> e.emit(boardDeleted, selectedBoard.getName()));
                    }
                    indexClientEndpoint.emit(WhiteboardServer.unshareBoard, boardname);
                }
            }
        }
        updateComboBox(null);
    }

    /**
     * Create a new local board with name peer:port:boardid.
     * The boardid includes the time stamp that the board was created at.
     */
    public void createBoard() {
        String name = peerport + ":board" + Instant.now().toEpochMilli();
        Whiteboard whiteboard = new Whiteboard(name, false);
        addBoard(whiteboard, true);
    }

    /**
     * Add a path to the selected board. The path has already
     * been drawn on the draw area; so if it can't be accepted then
     * the board needs to be redrawn without it.
     *
     * @param currentPath
     */
    public void pathCreatedLocally(WhiteboardPath currentPath) {
        if (selectedBoard != null) {
            if (!selectedBoard.addPath(currentPath, selectedBoard.getVersion())) {
                // some other peer modified the board in between
                drawSelectedWhiteboard(); // just redraw the screen without the path
            } else {
                // was accepted locally, so do remote stuff if needed
                if (selectedBoard.isRemote()) {
                    //Editor Mode
                    //Emit updates to board's owner first. Then let owner emit updates to other peers.
                    // Other update methods below runs the same way.
                    Endpoint endpoint = peerClientEndpoints.get(selectedBoard.getName());
                    endpoint.emit(boardPathUpdate, myEventInfo(getLatestPath(selectedBoard.toString())));
                    endpoint.on(boardPathAccepted, args ->
                            log.info("boardPathUpdate is accepted:" + args[0])
                    ).on(boardError, args ->
                            log.severe((String) args[0]));
                } else {
                    //Owner Mode
                    if (peerServerEndpoints.containsKey(selectedBoard.getName())) {
                        Set<Endpoint> peerServerEndpoint = this.peerServerEndpoints.get(selectedBoard.getName());
                        peerServerEndpoint.forEach(e -> {
                            e.emit(boardPathUpdate, myEventInfo(getLatestPath(selectedBoard.toString())));
                            e.on(boardPathAccepted, args ->
                                    log.info("boardPathUpdate is accepted:" + args[0])
                            ).on(boardError, args ->
                                    log.severe((String) args[0]));
                        });
                    }
                }

            }
        } else {
            log.severe("path created without a selected board: " + currentPath);
        }
    }

    /**
     * Clear the selected whiteboard.
     */
    public void clearedLocally() {
        if (selectedBoard != null) {
            if (!selectedBoard.clear(selectedBoard.getVersion())) {
                // some other peer modified the board in between
                drawSelectedWhiteboard();
            } else {
                // was accepted locally, so do remote stuff if needed
                drawSelectedWhiteboard();
                if (selectedBoard.isRemote()) {
                    //Editor Mode
                    Endpoint endpoint = peerClientEndpoints.get(selectedBoard.getName());
                    endpoint.emit(boardClearUpdate, myEventInfo(selectedBoard.getNameAndVersion()));
                    endpoint.on(boardClearAccepted, args ->
                            log.info("boardClearUpdate is accepted: " + args[0])
                    ).on(boardError, args ->
                            log.severe((String) args[0]));
                } else {
                    //Owner Mode
                    if (peerServerEndpoints.containsKey(selectedBoard.getName())) {
                        Set<Endpoint> peer_server_endpoints = peerServerEndpoints.get(selectedBoard.getName());
                        peer_server_endpoints.forEach(e -> {
                            e.emit(boardClearUpdate, myEventInfo(selectedBoard.getNameAndVersion()));
                            e.on(boardClearAccepted, args ->
                                    log.info("boardClearUpdate is accepted: " + args[0])
                            ).on(boardError, args ->
                                    log.severe((String) args[0]));
                        });
                    }
                }

            }
        } else {
            log.severe("cleared without a selected board");
        }
    }


    /**
     * Undo the last path of the selected whiteboard.
     */
    public void undoLocally() {
        if (selectedBoard != null) {
            if (!selectedBoard.undo(selectedBoard.getVersion())) {
                // some other peer modified the board in between
                drawSelectedWhiteboard();
            } else {
                drawSelectedWhiteboard();
                if (selectedBoard.isRemote()) {
                    //Editor Mode
                    Endpoint endpoint = peerClientEndpoints.get(selectedBoard.getName());
                    endpoint.emit(boardUndoUpdate, myEventInfo(selectedBoard.getNameAndVersion()));
                    endpoint.on(boardUndoAccepted, args ->
                            log.info("boardUndoUpdate is accepted: " + args[0])
                    ).on(boardError, args ->
                            log.severe((String) args[0]));
                } else {
                    //Owner Mode
                    if (peerServerEndpoints.containsKey(selectedBoard.getName())) {
                        Set<Endpoint> peer_server_endpoints = peerServerEndpoints.get(selectedBoard.getName());
                        peer_server_endpoints.forEach(e -> {
                            e.emit(boardUndoUpdate, myEventInfo(selectedBoard.getNameAndVersion()));
                            e.on(boardUndoAccepted, args ->
                                    log.info("boardUndpUpdate is accepted: " + args[0])
                            ).on(boardError, args ->
                                    log.severe((String) args[0]));
                        });
                    }
                }
            }
        } else {
            log.severe("undo without a selected board");
        }
    }

    /**
     * The variable selectedBoard has been set.
     */
    public void selectedABoard() {
        drawSelectedWhiteboard();
        log.info("selected board: " + selectedBoard.getName());

        //To send the listenBoard event to peer itself to keep listening the selection of board.
        // It should only be captured by peer's own.
        // Then the event should trigger the collaboration.
        if (indexClientEndpoint != null) {
            indexClientEndpoint.emit(listenBoard, myEventInfo(selectedBoard.getName()));
        }
    }

    /**
     * Set the share status on the selected board.
     */
    public void setShare(boolean share) {
        if (selectedBoard != null) {
            selectedBoard.setShared(share);
            if (share) {
//                System.out.println("CHANDLER SHARES FOOD");
                indexClientEndpoint.emit(WhiteboardServer.shareBoard, selectedBoard.getName());
            } else {
//                System.out.println("JOEY DOESN'T SHARE FOOD");
                indexClientEndpoint.emit(WhiteboardServer.unshareBoard, selectedBoard.getName());
            }
            // Also need to send something so that the whiteboard peer could capture up-to-date status.
            selectedABoard();
        } else {
            log.severe("there is no selected board");
        }
    }

    /**
     * Called by the gui when the user closes the app.
     */
    public void guiShutdown() {
        // do some final cleanup
        HashSet<Whiteboard> existingBoards = new HashSet<>(whiteboards.values());
        existingBoards.forEach((board) -> {
            deleteBoard(board.getName());
        });
        whiteboards.values().forEach((whiteboard) -> {
            String boardName = whiteboard.getName();
            Endpoint clientEndpoint = peerClientEndpoints.get(boardName);
            if (clientEndpoint != null) {
                clientEndpoint.emit(unlistenBoard, boardName);
                clientEndpoint.close();
            }
            Set<Endpoint> serverEndpoints = peerServerEndpoints.get(boardName);
            if (!serverEndpoints.isEmpty()) {
                serverEndpoints.forEach(Endpoint::close);
            }
        });
        indexClientEndpoint.close();
        waitToFinish();
    }


    /******
     *
     * GUI methods and callbacks from GUI for user actions.
     * You probably do not need to modify anything below here.
     *
     ******/

    /**
     * Redraw the screen with the selected board
     */
    public void drawSelectedWhiteboard() {
        drawArea.clear();
        if (selectedBoard != null) {
            selectedBoard.draw(drawArea);
        }
    }

    /**
     * Setup the Swing components and start the Swing thread, given the
     * peer's specific information, i.e. peer:port string.
     */
    public void show(String peerport) {
        // create main frame
        JFrame frame = new JFrame("Whiteboard Peer: " + peerport);
        Container content = frame.getContentPane();
        // set layout on content pane
        content.setLayout(new BorderLayout());
        // create draw area
        drawArea = new DrawArea(this);

        // add to content pane
        content.add(drawArea, BorderLayout.CENTER);

        // create controls to apply colors and call clear feature
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        /**
         * Action listener is called by the GUI thread.
         */
        ActionListener actionListener = new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (e.getSource() == clearBtn) {
                    clearedLocally();
                } else if (e.getSource() == blackBtn) {
                    drawArea.setColor(Color.black);
                } else if (e.getSource() == redBtn) {
                    drawArea.setColor(Color.red);
                } else if (e.getSource() == boardComboBox) {
                    if (modifyingComboBox) return;
                    if (boardComboBox.getSelectedIndex() == -1) return;
                    String selectedBoardName = (String) boardComboBox.getSelectedItem();
                    if (whiteboards.get(selectedBoardName) == null) {
                        log.severe("selected a board that does not exist: " + selectedBoardName);
                        return;
                    }
                    selectedBoard = whiteboards.get(selectedBoardName);
                    // remote boards can't have their shared status modified
                    if (selectedBoard.isRemote()) {
                        sharedCheckbox.setEnabled(false);
                        sharedCheckbox.setVisible(false);
                    } else {
                        modifyingCheckBox = true;
                        sharedCheckbox.setSelected(selectedBoard.isShared());
                        modifyingCheckBox = false;
                        sharedCheckbox.setEnabled(true);
                        sharedCheckbox.setVisible(true);
                    }
                    selectedABoard();
                } else if (e.getSource() == createBoardBtn) {
                    createBoard();
                } else if (e.getSource() == undoBtn) {
                    if (selectedBoard == null) {
                        log.severe("there is no selected board to undo");
                        return;
                    }
                    undoLocally();
                } else if (e.getSource() == deleteBoardBtn) {
                    if (selectedBoard == null) {
                        log.severe("there is no selected board to delete");
                        return;
                    }
                    deleteBoard(selectedBoard.getName());
                }
            }
        };

        clearBtn = new JButton("Clear Board");
        clearBtn.addActionListener(actionListener);
        clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
        clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        blackBtn = new JButton("Black");
        blackBtn.addActionListener(actionListener);
        blackBtn.setToolTipText("Draw with black pen");
        blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        redBtn = new JButton("Red");
        redBtn.addActionListener(actionListener);
        redBtn.setToolTipText("Draw with red pen");
        redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        deleteBoardBtn = new JButton("Delete Board");
        deleteBoardBtn.addActionListener(actionListener);
        deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
        deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        createBoardBtn = new JButton("New Board");
        createBoardBtn.addActionListener(actionListener);
        createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
        createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        undoBtn = new JButton("Undo");
        undoBtn.addActionListener(actionListener);
        undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
        undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        sharedCheckbox = new JCheckBox("Shared");
        sharedCheckbox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (!modifyingCheckBox) setShare(e.getStateChange() == 1);
            }
        });
        sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
        sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);


        // create a drop list for boards to select from
        JPanel controlsNorth = new JPanel();
        boardComboBox = new JComboBox<String>();
        boardComboBox.addActionListener(actionListener);


        // add to panel
        controlsNorth.add(boardComboBox);
        controls.add(sharedCheckbox);
        controls.add(createBoardBtn);
        controls.add(deleteBoardBtn);
        controls.add(blackBtn);
        controls.add(redBtn);
        controls.add(undoBtn);
        controls.add(clearBtn);

        // add to content pane
        content.add(controls, BorderLayout.WEST);
        content.add(controlsNorth, BorderLayout.NORTH);

        frame.setSize(600, 600);

        // create an initial board
        createBoard();

        // closing the application
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(frame,
                        "Are you sure you want to close this window?", "Close Window?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    guiShutdown();
                    frame.dispose();
                }
            }
        });

        // show the swing paint result
        frame.setVisible(true);

    }

    /**
     * Update the GUI's list of boards. Note that this method needs to update data
     * that the GUI is using, which should only be done on the GUI's thread, which
     * is why invoke later is used.
     *
     * @param select, board to select when list is modified or null for default
     *                selection
     */
    private void updateComboBox(String select) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                modifyingComboBox = true;
                boardComboBox.removeAllItems();
                int anIndex = -1;
                synchronized (whiteboards) {
                    ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
                    Collections.sort(boards);
                    for (int i = 0; i < boards.size(); i++) {
                        String boardname = boards.get(i);
                        boardComboBox.addItem(boardname);
                        if (select != null && select.equals(boardname)) {
                            anIndex = i;
                        } else if (anIndex == -1 && selectedBoard != null &&
                                selectedBoard.getName().equals(boardname)) {
                            anIndex = i;
                        }
                    }
                }
                modifyingComboBox = false;
                if (anIndex != -1) {
                    boardComboBox.setSelectedIndex(anIndex);
                } else {
                    if (whiteboards.size() > 0) {
                        boardComboBox.setSelectedIndex(0);
                    } else {
                        drawArea.clear();
                        createBoard();
                    }
                }

            }
        });
    }

}
