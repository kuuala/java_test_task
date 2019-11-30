package test.grepgui.controllers;

import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import test.grepgui.model.FileResult;
import test.grepgui.model.FileTask;
import test.grepgui.model.Searcher;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResultWindowController implements Initializable {

    @FXML
    private SplitPane resultWindow;
    @FXML
    private TreeView<String> resultTreeView;
    @FXML
    private TabPane resultTabPane;

    final private String path;
    final private String text;
    final private String extension;

    ResultWindowController(String path, String text, String extension) {
        this.path = path;
        this.text = text;
        this.extension = extension;
    }

    @FXML
    private void upClickButton() {}

    @FXML
    private void downClickButton() {}

    private void setClipboard(String string) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(string);
        clipboard.setContent(clipboardContent);
    }

    @FXML
    private void copyAllClickButton() {
        SingleSelectionModel<Tab> selectionModel =  resultTabPane.getSelectionModel();
        TextArea area =  (TextArea) selectionModel.getSelectedItem().getContent();
        area.selectAll();
        setClipboard(area.getText());
    }

    private void addElementOnTree(File file) {
        synchronized (resultTreeView) {
            if (resultTreeView.getRoot() == null) {
                resultTreeView.setRoot(new TreeItem<>(FilenameUtils.getName(path)));
            }
            TreeItem<String> item = new TreeItem<>(file.getAbsolutePath());
            resultTreeView.getRoot().getChildren().add(item);
        }
    }

    private boolean itemIsLeaf(Object item) {
        return item.getClass() == TreeItem.class && ((TreeItem) item).getChildren().size() == 0;
    }

    private Tab createTab(File file, String content) {
        Label tabLabel = new Label(file.getName());
        TextArea tabText = new TextArea();
        tabText.appendText(content);
        Tab tab = new Tab();
        tab.setContent(tabText);
        tab.setGraphic(tabLabel);
        return tab;
    }

    private void addTabToTabPane(Tab tab) {
        resultTabPane.getTabs().add(tab);
        SingleSelectionModel<Tab> selectionModel =  resultTabPane.getSelectionModel();
        selectionModel.select(tab);
    }

    @FXML
    private void treeViewMouseClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            Object selectedItem = resultTreeView.getSelectionModel().getSelectedItem();
            if (itemIsLeaf(selectedItem)) {
                TreeItem<String> item = (TreeItem) selectedItem;
                File file = new File(item.getValue());
                StringBuilder content = new StringBuilder();
                try (FileInputStream inputStream = new FileInputStream(file)) {
                    content.append(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Tab tab = createTab(file, content.toString());
                addTabToTabPane(tab);
            }
        }
    }

    private void runTasks(List<FileTask> tasks) {
        ExecutorService service = Executors.newFixedThreadPool(4);
        for (FileTask task: tasks) {
            task.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
                    workerStateEvent -> {
                        Optional<FileResult> result = task.getValue();
                        result.ifPresent(x -> addElementOnTree(x.getFile()));
                    });
            service.submit(task);
        }
        service.shutdown();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        Searcher searcher = new Searcher(path, text, extension);
        List<FileTask> tasks = searcher.getTasks();
        runTasks(tasks);
    }
}
