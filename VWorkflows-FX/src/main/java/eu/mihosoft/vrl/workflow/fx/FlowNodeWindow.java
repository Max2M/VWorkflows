/*
 * FlowNodeWindow.java
 * 
 * Copyright 2012-2013 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 * 
 * Please cite the following publication(s):
 *
 * M. Hoffer, C.Poliwoda, G.Wittum. Visual Reflection Library -
 * A Framework for Declarative GUI Programming on the Java Platform.
 * Computing and Visualization in Science, 2011, in press.
 *
 * THIS SOFTWARE IS PROVIDED BY Michael Hoffer <info@michaelhoffer.de> "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Michael Hoffer <info@michaelhoffer.de> OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of Michael Hoffer <info@michaelhoffer.de>.
 */
package eu.mihosoft.vrl.workflow.fx;

import eu.mihosoft.vrl.workflow.Connection;
import eu.mihosoft.vrl.workflow.skin.ConnectionSkin;
import eu.mihosoft.vrl.workflow.Connections;
import eu.mihosoft.vrl.workflow.Connector;
import eu.mihosoft.vrl.workflow.VFlow;
import eu.mihosoft.vrl.workflow.VFlowModel;
import eu.mihosoft.vrl.workflow.VNode;
import eu.mihosoft.vrl.workflow.skin.VNodeSkin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import jfxtras.scene.control.window.CloseIcon;
import jfxtras.scene.control.window.MinimizeIcon;
import jfxtras.scene.control.window.Window;
import jfxtras.scene.control.window.WindowIcon;
//import jfxtras.labs.scene.control.window.CloseIcon;
//import jfxtras.labs.scene.control.window.MinimizeIcon;
//import jfxtras.labs.scene.control.window.Window;
//import jfxtras.labs.scene.control.window.WindowIcon;
import jfxtras.scene.control.window.WindowUtil;

/**
 *
 * @author Michael Hoffer &lt;info@michaelhoffer.de&gt;
 */
public final class FlowNodeWindow extends Window {

    private final ObjectProperty<FXFlowNodeSkin> nodeSkinProperty
            = new SimpleObjectProperty<>();
    private VCanvas content;
    private OptimizableContentPane parentContent;
    private final CloseIcon closeIcon = new CloseIcon(this);
    private final MinimizeIcon minimizeIcon = new MinimizeIcon(this);

    private final ChangeListener<Boolean> selectionListener;

    public FlowNodeWindow(final FXFlowNodeSkin skin) {

        nodeSkinProperty().set(skin);

        setEditableState(true);

        if (skin.getModel() instanceof VFlowModel) {

            VFlowModel flowNodeModel = (VFlowModel) skin.getModel();

            parentContent = new OptimizableContentPane();
            content = new VCanvas();
            content.setPadding(new Insets(5));
            content.setMinScaleX(0.01);
            content.setMinScaleY(0.01);
            content.setMaxScaleX(1);
            content.setMaxScaleY(1);
            content.setScaleBehavior(ScaleBehavior.IF_NECESSARY);
            content.setTranslateToMinNodePos(true);
            content.setTranslateBehavior(TranslateBehavior.IF_NECESSARY);

            addResetViewMenu(content);

            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);

            parentContent.getChildren().add(scrollPane);
            super.setContentPane(parentContent);

            InvalidationListener refreshViewListener = (o) -> {
                content.resetScale();
                content.resetTranslation();
            };

            widthProperty().addListener(refreshViewListener);
            heightProperty().addListener(refreshViewListener);
            flowNodeModel.visibleProperty().addListener(refreshViewListener);
        }

        addCollapseIcon(skin);
        configureCanvas(skin);

        addEventHandler(MouseEvent.MOUSE_ENTERED, (MouseEvent t) -> {
            connectorsToFront();
        });

        setSelectable(skin.getModel().isSelectable());
        skin.getModel().selectableProperty().bindBidirectional(this.selectableProperty());

        WindowUtil.getDefaultClipboard().select(FlowNodeWindow.this, skin.getModel().isSelected());

        selectionListener = (ov, oldValue, newValue) -> {
            System.out.println("SEL: " + newValue);
            WindowUtil.getDefaultClipboard().select(FlowNodeWindow.this, newValue);
        };

        skin.getModel().selectedProperty().addListener(selectionListener);

        skin.getModel().requestSelection(FlowNodeWindow.this.isSelected());
        FlowNodeWindow.this.selectedProperty().addListener((ov, oldValue, newValue) -> {
            skin.getModel().requestSelection(newValue);
        });

        skinProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                Node titlebar = newValue.getNode().lookup("." + getTitleBarStyleClass());

                titlebar.addEventHandler(MouseEvent.ANY, (MouseEvent evt) -> {
                    if (evt.getClickCount() == 1
                            && evt.getEventType() == MouseEvent.MOUSE_RELEASED
                            && evt.isDragDetect()) {
                        skin.getModel().requestSelection(!skin.getModel().isSelected());
                    }
                });
            }
        });

        onClosedActionProperty().addListener((ov, oldValue, newValue) -> {
            onRemovedFromSceneGraph();
        });

//        // TODO shouldn't leaf nodes also have a visibility property?
//        if (nodeSkinProperty.get().getModel() instanceof VFlowModel) {
//            VFlowModel model = (VFlowModel) nodeSkinProperty.get().getModel();
//            model.visibleProperty().addListener(new ChangeListener<Boolean>() {
//                @Override
//                public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
//                    
//                    for (Node n : content.getContentPane().getChildren()) {
//                        if (n instanceof Window) {
//                            Window w = (Window) n;
//                            w.requestLayout();
//                            w.getContentPane().requestLayout();
//                        }
//                    }
//                    
////                    System.out.println("TEST");
//////                    parentContent.requestOptimization();
////                    requestLayout();
////                    getParent().requestLayout();
//////                    requestParentLayout();
////                    content.requestLayout();
////                    content.getContentPane().requestLayout();
//                }
//            });
//        }
    }

    ObservableList<Node> getChildrenModifiable() {
        return super.getChildren();
    }

    public static void addResetViewMenu(VCanvas canvas) {
        final ContextMenu cm = new ContextMenu();
        MenuItem resetViewItem = new MenuItem("Reset View");
        resetViewItem.setOnAction((ActionEvent e) -> {
            canvas.resetTranslation();
            canvas.resetScale();
        });
        cm.getItems().add(resetViewItem);
        canvas.addEventHandler(MouseEvent.MOUSE_CLICKED, (MouseEvent e) -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                cm.show(canvas, e.getScreenX(), e.getScreenY());
            }
        });
    }

    private void showFlowInWindow(VFlow flow, List<String> stylesheets, String title) {

        // create scalable root pane
        VCanvas canvas = new VCanvas();
        addResetViewMenu(canvas);
        canvas.setMinScaleX(0.2);
        canvas.setMinScaleY(0.2);
        canvas.setMaxScaleX(1);
        canvas.setMaxScaleY(1);

        canvas.setTranslateToMinNodePos(true);

        canvas.setScaleBehavior(ScaleBehavior.IF_NECESSARY);
        canvas.setTranslateBehavior(TranslateBehavior.IF_NECESSARY);
//        canvas.setStyle("-fx-border-color: red");
        canvas.getContent().setStyle("-fx-border-color: green");

        canvas.translateBehaviorProperty().addListener((ov, oldV, newV) -> {
            System.out.println("-new-val: " + newV);
        });

        // create skin factory for flow visualization
        FXSkinFactory fXSkinFactory
                = nodeSkinProperty.get().getSkinFactory().
                newInstance(canvas.getContent(), null);

        // generate the ui for the flow
        flow.addSkinFactories(fXSkinFactory);

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        // the usual application setup
        Scene scene = new Scene(scrollPane, 800, 800);

        scene.getStylesheets().setAll(stylesheets);

        VFlow rootFlow = flow.getRootFlow();

        Stage stage = new Stage() {
            {
//
//                String nodeId = FlowNodeWindow.this.
//                        nodeSkinProperty().get().getModel().getId();

//                Stage stage = this;
//                rootFlow.getNodes().addListener(
//                        (ListChangeListener.Change<? extends VNode> c) -> {
//                            while (c.next()) {
//                                if (c.wasAdded()) {
//                                    for (VNode n : c.getAddedSubList()) {
//                                        if (n.getId().equals(nodeId)) {
//                                            canvas.getContent().getChildren().clear();
//                                            VFlow flow = (VFlow) rootFlow.getFlowById(n.getId());
//                                            flow.addSkinFactories(new FXValueSkinFactory(null));
//                                        }
//                                    }
//                                }
//                                
//                                 if (c.wasRemoved()) {
//                                    for (VNode n : c.getRemoved()) {
//                                        if (n.getId().equals(nodeId)) {
//                                            System.out.println("close");
//                                           stage.close();
//                                        }
//                                    }
//                                }
//                            }
//                        });
            }

        };

        stage.setWidth(800);
        stage.setHeight(600);

        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();

//        stage.setOnCloseRequest((e) -> {
//            for (VNode n : rootFlow.getNodes()) {
//                VNodeSkin<?> skin = rootFlow.getNodeSkinLookup().
//                        getById(fXSkinFactory, n.getId());
//                if (skin != null) {
//                    skin.remove();
//                }
//            }
//        });
    }

    public Pane getWorkflowContentPane() {
        return content.getContent();
    }

    /**
     * @return the nodeSkinProperty
     */
    public final ObjectProperty<FXFlowNodeSkin> nodeSkinProperty() {
        return nodeSkinProperty;
    }

    private void addCollapseIcon(FXFlowNodeSkin skin) {

        if (skin == null) {
            return;
        }

        if (!(skin.getModel() instanceof VFlowModel)) {
            return;
        }

        final WindowIcon collapseIcon = new WindowIcon();

        collapseIcon.setOnAction((ActionEvent t) -> {
            FXFlowNodeSkin skin1 = nodeSkinProperty.get();
            if (skin1 != null) {
                VFlowModel model = (VFlowModel) skin1.getModel();
                model.setVisible(!model.isVisible());
            }
        });

        getRightIcons().add(collapseIcon);

        if (skin.modelProperty() != null) {
            skin.modelProperty().addListener((ov, oldValue, newValue) -> {
                if (newValue instanceof VFlowModel) {
                    getRightIcons().add(collapseIcon);
                } else {
                    getRightIcons().remove(collapseIcon);
                }
            });
        }

        // adds an icon that opens a new view in a separate window
        final WindowIcon newViewIcon = new WindowIcon();

        newViewIcon.setOnAction((ActionEvent t) -> {
            FXFlowNodeSkin skin1 = nodeSkinProperty.get();
            if (skin1 != null) {
                String nodeId = skin1.getModel().getId();
                for (VFlow vf : skin1.getController().getSubControllers()) {
                    if (vf.getModel().getId().equals(nodeId)) {
                        showFlowInWindow(vf,
                                NodeUtil.getStylesheetsOfAncestors(
                                        FlowNodeWindow.this),
                                getLocation(vf));
                        break;
                    }
                }
            }
        });

        getLeftIcons().add(newViewIcon);

    }

    private String getLocation(VFlow f) {

        VFlowModel parent = f.getModel().getFlow();

        List<String> names = new ArrayList<>();

        names.add(f.getModel().getTitle());

        while (parent != null) {
            names.add(parent.getTitle());
            parent = parent.getFlow();
        }

        Collections.reverse(names);

        StringBuilder sb = new StringBuilder();

        names.forEach(n -> sb.append("/").append(n));

        return sb.toString();
    }

    @Override
    public void toFront() {
        super.toFront();
        connectorsToFront();
    }

    private void connectorsToFront() {
        // move connectors to front
        FXFlowNodeSkin skin = nodeSkinProperty().get();

        for (List<Shape> shapeList : skin.shapeLists) {
            for (Node n : shapeList) {
                n.toFront();
            }
        }

        List<Connection> connections = new ArrayList<>();

        for (Connector connector : skin.connectors.keySet()) {
            for (Connections connectionsI : skin.controller.getAllConnections().values()) {
                connections.addAll(connectionsI.getAllWith(connector));
            }
        }

        for (Connection conn : connections) {
            ConnectionSkin skinI = skin.controller.getNodeSkinLookup().
                    getById(skin.getSkinFactory(), conn);

            if (skinI instanceof FXConnectionSkin) {
                FXConnectionSkin fxSkin = (FXConnectionSkin) skinI;
                fxSkin.toFront();
            }
        }
    }

    private void configureCanvas(FXFlowNodeSkin skin) {

//        if (skin == null) {
//            return;
//        }
//
//        if ((skin.getModel() instanceof VFlowModel)) {
//            return;
//        }
        if (content != null) {
            content.getStyleClass().setAll("vnode-content");
            skin.configureCanvas(content);
        }
    }

    void onRemovedFromSceneGraph() {
        nodeSkinProperty().get().getModel().selectedProperty().
                removeListener(selectionListener);
    }

    private void setCloseableState(boolean b) {
        if (b) {
            if (!getLeftIcons().contains(closeIcon)) {
                getLeftIcons().add(closeIcon);
            }
        } else {
            getLeftIcons().remove(closeIcon);
        }
    }

    private void setMinimizableState(boolean b) {
        if (b) {
            if (!getLeftIcons().contains(minimizeIcon)) {
                getLeftIcons().add(minimizeIcon);
            }
        } else {
            getLeftIcons().remove(minimizeIcon);
        }
    }

    final void setEditableState(boolean b) {
        setCloseableState(b);
        setMinimizableState(b);
        setSelectable(b);
    }
}
