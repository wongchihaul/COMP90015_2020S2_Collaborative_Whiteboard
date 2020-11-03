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
//	String peerport="standalone"; // a default value for the non-distributed version
    String peerport;
    String whiteboardServerHost;
    int whiteboardServerPort;
    Endpoint indexClientEndpoint;
    final Map<String, Endpoint> peerClientEndpoints;                // boardname <-> endpoint
    final Map<String, Set<Endpoint>> peerServerEndpoints;
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
        this.whiteboardServerHost = whiteboardServerHost;
        this.whiteboardServerPort = whiteboardServerPort;
        show(String.valueOf(peerPort));
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
     */
    public static String getLatestPath(String data) {
        String[] paths = getBoardPaths(data).split("%");
        String boardIDAndVersion = getBoardName(data) + "%" + getBoardVersion(data) + "%";
        return paths.length > 1 ? boardIDAndVersion + paths[paths.length - 1] : boardIDAndVersion;
    }

    /**
     * @param data = peer:port:boardid%version%PATHS
     * @return peer:port
     */
    public static String getPeerPort(String data) {
        String[] parts = data.split(":");
        return parts[0] + ":" + parts[1];
    }


    /******
     *
     * Methods called from events.
     *
     ******/

    // From whiteboard server
    // TODO
    public void startAsClient() {
        PeerManager peerManager = new PeerManager(getPort(peerport));
        try {
            ClientManager clientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
            clientManager.on(PeerManager.peerStarted, args -> {
                this.indexClientEndpoint = (Endpoint) args[0];
                indexClientEndpoint.on(WhiteboardServer.sharingBoard, args2 -> {
                    if (!getPeerPort((String) args2[0]).equals(peerport)) {
                        System.out.println("Adding others' board now");
                        //每当有新whiteboard分享的时候，其余peer都会被动在list中加上这个whiteboard
                        addBoard(new Whiteboard((String) args2[0], true), false);
                    }
                }).on(WhiteboardServer.unsharingBoard, args2 -> {
                    if (!getPeerPort((String) args2[0]).equals(peerport)) {
                        deleteBoard((String) args2[0]);
                    }
                });

            }).on(PeerManager.peerStopped, args -> {
                Endpoint endpoint = (Endpoint) args[0];
                System.out.println("Disconnected from the index server: " + endpoint.getOtherEndpointId());
            }).on(PeerManager.peerError, args -> {
                Endpoint endpoint = (Endpoint) args[0];
                System.out.println("There was an error communicating with the index server: "
                        + endpoint.getOtherEndpointId());
            }).on(PeerManager.peerServerManager, args -> {
                ServerManager serverManager = (ServerManager) args[0];
                serverManager.on(IOThread.ioThread, args1 -> {
                    String port = (String) args1[0];
                    // we don't need this info, but let's log it
                    log.info("using Internet address: " + port);
                });
                try {
                    collaborate(peerManager);                            // Not sure
                } catch (UnknownHostException | InterruptedException e) {
                    e.printStackTrace();
                }
            });
            peerManager.start();
            clientManager.start();
            clientManager.join();                        //Not sure
        } catch (UnknownHostException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    // From whiteboard peer
    //TODO
    public void collaborate(PeerManager peerManager) throws InterruptedException, UnknownHostException {    //都是由peermanager管理的
        if (selectedBoard != null) {
            if (selectedBoard.isRemote()) {
                // Editor Mode.
                // Need to connect to the owner peer of the selected whiteboard
                editorMode(peerManager, selectedBoard);
            } else {
                // You're the owner of selected board
                ownerMode(peerManager);
            }
        } else {
            log.severe("collaborating without a selected board: " + peerManager);
        }
    }


    public void editorMode(PeerManager peerManager, Whiteboard selectedBoard) throws InterruptedException, UnknownHostException {
        String peerHost = getIP(selectedBoard.toString());
        int peerServerPort = getPort(selectedBoard.toString());
        ClientManager clientManager = peerManager.connect(peerServerPort, peerHost);
        clientManager.on(PeerManager.peerStarted, args -> {
            Endpoint endpoint = (Endpoint) args[0];
            System.out.println("Connection from peer: " + endpoint.getOtherEndpointId());

            synchronized (peerClientEndpoints) {
                peerClientEndpoints.put(selectedBoard.getName(), endpoint);
            }

            //send listen and getData request to the owner of selected whiteboard.
            endpoint.emit(listenBoard, selectedBoard.getName());
            endpoint.emit(getBoardData, selectedBoard.getName());
            //遍历whiteboards，找到全部非selected的whiteboard，发送unlisten给那些whiteboard的owner
            //并且关闭对应endpoint，等到下次重新连接时再创建endpoint
            whiteboards.forEach((name, board) -> {
                if ((!name.equals(selectedBoard.getName())) && board.isRemote()) {
                    Endpoint backgroundWhiteboard = peerClientEndpoints.get(selectedBoard.getName());
                    backgroundWhiteboard.emit(unlistenBoard, name);
                    backgroundWhiteboard.close();
                    synchronized (peerClientEndpoints) {
                        peerClientEndpoints.remove(selectedBoard.getName());
                    }
                }
            });


            endpoint.on(boardData, args1 -> {
                if (getBoardName((String) args1[0]).equals(selectedBoard.getName())) {
                    selectedBoard.whiteboardFromString
                            (getBoardName((String) args1[0]), getBoardData((String) args1[0]));
                }
            }).on(boardPathUpdate, args1 -> {
                if (getBoardName((String) args1[0]).equals(selectedBoard.getName())) {
                    if (getBoardVersion((String) args1[0]) == getBoardVersion(selectedBoard.toString())) {
                        pathCreatedLocally(new WhiteboardPath(getBoardPaths((String) args1[0])));
                        endpoint.emit(boardPathAccepted, args1[0]);
                    } else {
                        endpoint.emit(boardError, " Version mismatched. Requesting for " +
                                "the owner's current version of the whiteboard");
                        endpoint.emit(getBoardData, selectedBoard.getName());
                    }
                }
            }).on(boardUndoUpdate, args1 -> {
                if (getBoardName((String) args1[0]).equals(selectedBoard.getName())) {
                    if (getBoardVersion((String) args1[0]) == getBoardVersion(selectedBoard.toString())) {
                        undoLocally();
                        endpoint.emit(boardUndoAccepted, args1[0]);
                    } else {
                        endpoint.emit(boardError, " Version mismatched. Requesting for " +
                                "the owner's current version of the whiteboard");
                        endpoint.emit(getBoardData, selectedBoard.getName());
                    }
                }
            }).on(boardClearUpdate, args1 -> {
                if (getBoardName((String) args1[0]).equals(selectedBoard.getName())) {
                    if (getBoardVersion((String) args1[0]) == getBoardVersion(selectedBoard.toString())) {
                        clearedLocally();
                        endpoint.emit(boardClearAccepted, args1[0]);
                    } else {
                        endpoint.emit(boardError, " Version mismatched. Requesting for " +
                                "the owner's current version of the whiteboard");
                        endpoint.emit(getBoardData, selectedBoard.getName());
                    }
                }
            }).on(boardDeleted, args1 ->
                    deleteBoard((String) args1[0])
            ).on(boardError, args1 -> System.out.println(args1[0]));
        }).on(PeerManager.peerStopped, args -> {
            Endpoint endpoint = (Endpoint) args[0];
            System.out.println("Disconnected from the index server: " + endpoint.getOtherEndpointId());
        }).on(PeerManager.peerError, args -> {
            Endpoint endpoint = (Endpoint) args[0];
            System.out.println("There was an error communicating with the index server: "
                    + endpoint.getOtherEndpointId());
        });

        clientManager.start();
    }

    public void ownerMode(PeerManager peerManager) {
        peerManager.on(PeerManager.peerServerManager, args -> {
            ServerManager serverManager = (ServerManager) args[0];
            serverManager.on(ServerManager.sessionStarted, args1 -> {
                Endpoint endpoint = (Endpoint) args1[0];
                //==================
                System.out.println("Client session started: " + endpoint.getOtherEndpointId());
                //==================

                endpoint.on(listenBoard, args2 -> {
                    String boardRequested = (String) args2[0];
                    if (whiteboards.containsKey(boardRequested)) {
                        synchronized (peerServerEndpoints) {
                            if (!peerServerEndpoints.containsKey(selectedBoard.getName())) {
                                peerServerEndpoints.put(selectedBoard.getName(), new HashSet<>());
                            }
                            peerServerEndpoints.get(selectedBoard.getName()).add(endpoint);
                        }
                    } else {
                        endpoint.emit(boardError, "Whiteboard listened does not exist");
                    }
                }).on(unlistenBoard, args2 -> {
                    String boardRequested = (String) args2[0];
                    if (whiteboards.containsKey(boardRequested)) {
                        synchronized (peerServerEndpoints) {
                            peerServerEndpoints.get(selectedBoard.getName()).remove(endpoint);
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
                });
            }).on(ServerManager.sessionStopped, (args1) -> {
                Endpoint endpoint = (Endpoint) args1[0];
                log.info("Client session ended: " + endpoint.getOtherEndpointId());
            }).on(ServerManager.sessionError, (args1) -> {
                Endpoint endpoint = (Endpoint) args1[0];
                log.warning("Client session ended in error: " + endpoint.getOtherEndpointId());
            }).on(IOThread.ioThread, (args1) -> {
                String peerport = (String) args1[0];
                // we don't need this info, but let's log it
                log.info("using Internet address: " + peerport);
            });
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
        //TODO
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
                if (!selectedBoard.isRemote()) {
                    Set<Endpoint> peerServerEndpoint = peerServerEndpoints.get(boardname);
                    if (peerServerEndpoint != null) {
                        peerServerEndpoint.forEach(e -> {
                            e.emit(boardDeleted, selectedBoard.getName());
                        });
                    }
                }
                whiteboards.remove(boardname);
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
                //TODO: how to avoid receiving event sent by its own.
                if (selectedBoard.isRemote()) {
                    //Editor Mode
                    //Emit updates to board's owner first.
                    Endpoint endpoint = peerClientEndpoints.get(selectedBoard.getName());
                    endpoint.emit(boardPathUpdate, getLatestPath(selectedBoard.toString()));
                    endpoint.on(boardPathAccepted, args ->
                            System.out.println(args[0])
                    ).on(boardError, args ->
                            System.out.println(args[0]));
                } else {
                    //Owner Mode
                    if (peerServerEndpoints.containsKey(selectedBoard.getName())) {
                        Set<Endpoint> peer_server_endpoints = peerServerEndpoints.get(selectedBoard.getName());
                        peer_server_endpoints.forEach(e -> {
                            e.emit(boardPathUpdate, getLatestPath(selectedBoard.toString()));
                            e.on(boardPathAccepted, args ->
                                    System.out.println(args[0])
                            ).on(boardError, args ->
                                    System.out.println(args[0]));
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
                //TODO: how to avoid receiving event sent by its own.
                drawSelectedWhiteboard();
                if (selectedBoard.isRemote()) {
                    //Editor Mode
                    //Emit updates to board's owner first.
                    Endpoint endpoint = peerClientEndpoints.get(selectedBoard.getName());
                    endpoint.emit(boardClearUpdate, selectedBoard.getNameAndVersion());
                    endpoint.on(boardClearAccepted, args ->
                            System.out.println(args[0])
                    ).on(boardError, args ->
                            System.out.println(args[0]));
                } else {
                    //Owner Mode
                    if (peerServerEndpoints.containsKey(selectedBoard.getName())) {
                        Set<Endpoint> peer_server_endpoints = peerServerEndpoints.get(selectedBoard.getName());
                        peer_server_endpoints.forEach(e -> {
                            e.emit(boardClearUpdate, selectedBoard.getNameAndVersion());
                            e.on(boardClearAccepted, args ->
                                    System.out.println(args[0])
                            ).on(boardError, args ->
                                    System.out.println(args[0]));
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
                //TODO: how to avoid receiving event sent by its own.
                drawSelectedWhiteboard();
                if (selectedBoard.isRemote()) {
                    //Editor Mode
                    //Emit updates to board's owner first.
                    Endpoint endpoint = peerClientEndpoints.get(selectedBoard.getName());
                    endpoint.emit(boardUndoUpdate, selectedBoard.getNameAndVersion());
                    endpoint.on(boardUndoAccepted, args ->
                            System.out.println(args[0])
                    ).on(boardError, args ->
                            System.out.println(args[0]));
                } else {
                    //Owner Mode
                    if (peerServerEndpoints.containsKey(selectedBoard.getName())) {
                        Set<Endpoint> peer_server_endpoints = peerServerEndpoints.get(selectedBoard.getName());
                        peer_server_endpoints.forEach(e -> {
                            e.emit(boardUndoUpdate, selectedBoard.getNameAndVersion());
                            e.on(boardUndoAccepted, args ->
                                    System.out.println(args[0])
                            ).on(boardError, args ->
                                    System.out.println(args[0]));
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
        //TODO: how to send event to peer itself to keep listening the selection of board.

        // It should only be captured by peer's own.
        // Then the event should trigger the collaboration.
//        indexClientEndpoint.localEmit(listenBoard, selectedBoard.getName());
    }

    /**
     * Set the share status on the selected board.
     */
    public void setShare(boolean share) {
        if (selectedBoard != null) {
            selectedBoard.setShared(share);
            if (share) {
                System.out.println("CHANDLER SHARES FOOD");
                indexClientEndpoint.emit(WhiteboardServer.shareBoard, selectedBoard.getName());
            } else {
                System.out.println("JOEY DOESN'T SHARE FOOD");
                indexClientEndpoint.emit(WhiteboardServer.unshareBoard, selectedBoard.getName());
            }
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
            //TODO
        });
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
