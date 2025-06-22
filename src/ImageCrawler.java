/**
 * @author Lin100101
 * @version 1.0
 */
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"all"})
public class ImageCrawler extends JFrame {
    private JTextField urlField;
    private JButton crawlButton;
    private JTextArea resultArea;
    private JList<ImageInfo> imageList;
    private DefaultListModel<ImageInfo> listModel;
    private JButton downloadButton;
    private JButton downloadAllButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    // 定义请求头，防止403错误
    private static final Map<String, String> REQUEST_HEADERS = new HashMap<>();
    static {
        REQUEST_HEADERS.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        REQUEST_HEADERS.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        REQUEST_HEADERS.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        REQUEST_HEADERS.put("Connection", "keep-alive");
    }

    public ImageCrawler() {
        setTitle("网页图片爬虫");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 顶部面板 - URL输入和爬虫按钮
        JPanel topPanel = new JPanel();
        urlField = new JTextField(50);
        urlField.setText("https://example.com");
        crawlButton = new JButton("开始爬取");
        topPanel.add(new JLabel("网页URL:"));
        topPanel.add(urlField);
        topPanel.add(crawlButton);
        add(topPanel, BorderLayout.NORTH);

        // 中间面板 - 图片列表和结果展示
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        listModel = new DefaultListModel<>();
        imageList = new JList<>(listModel);
        imageList.setCellRenderer(new ImageListCellRenderer());
        imageList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(imageList);
        splitPane.setLeftComponent(listScrollPane);

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane resultScrollPane = new JScrollPane(resultArea);
        splitPane.setRightComponent(resultScrollPane);

        splitPane.setDividerLocation(400);
        add(splitPane, BorderLayout.CENTER);

        // 底部面板 - 下载按钮和进度条
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        downloadButton = new JButton("下载选中图片");
        downloadAllButton = new JButton("下载全部图片");
        buttonPanel.add(downloadButton);
        buttonPanel.add(downloadAllButton);
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        statusLabel = new JLabel("就绪");
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.EAST);
        bottomPanel.add(progressPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // 添加事件监听器
        crawlButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                crawlImages();
            }
        });

        downloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloadSelectedImages();
            }
        });

        downloadAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloadAllImages();
            }
        });
    }

    private void crawlImages() {
        String url = urlField.getText();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入网页URL");
            return;
        }

        // 清空之前的结果
        listModel.clear();
        resultArea.setText("开始爬取 " + url + " 上的图片...\n");

        // 在单独的线程中执行爬虫任务，避免阻塞UI
        SwingWorker<List<ImageInfo>, Void> worker = new SwingWorker<List<ImageInfo>, Void>() {
            @Override
            protected List<ImageInfo> doInBackground() throws Exception {
                List<ImageInfo> imageInfos = new ArrayList<>();
                try {
                    // 连接到网页，添加请求头
                    Document doc = Jsoup.connect(url)
                            .headers(REQUEST_HEADERS)
                            .timeout(10000)
                            .get();
                    resultArea.append("成功获取网页内容\n");

                    // 查找所有图片元素
                    Elements imgElements = doc.select("img");
                    resultArea.append("找到 " + imgElements.size() + " 个图片元素\n");

                    // 处理每个图片元素
                    for (Element img : imgElements) {
                        String imgUrl = img.absUrl("src");
                        if (!imgUrl.isEmpty()) {
                            try {
                                // 获取图片大小
                                URL imageURL = new URL(imgUrl);
                                long size = getImageSize(imageURL);

                                ImageInfo imageInfo = new ImageInfo(imgUrl, size);
                                imageInfos.add(imageInfo);
                                resultArea.append("已获取图片: " + imgUrl + " (" + formatSize(size) + ")\n");
                            } catch (Exception ex) {
                                resultArea.append("无法获取图片 " + imgUrl + ": " + ex.getMessage() + "\n");
                            }
                        }
                    }

                    // 按图片大小排序（从大到小）
                    imageInfos.sort(Comparator.comparingLong(ImageInfo::getSize).reversed());

                    resultArea.append("\n图片爬取完成，共获取 " + imageInfos.size() + " 张图片\n");
                    if (!imageInfos.isEmpty()) {
                        resultArea.append("最大的图片: " + imageInfos.get(0).getUrl() + " (" +
                                formatSize(imageInfos.get(0).getSize()) + ")\n");
                    }
                } catch (IOException ex) {
                    resultArea.append("爬取失败: " + ex.getMessage() + "\n");
                }
                return imageInfos;
            }

            @Override
            protected void done() {
                try {
                    List<ImageInfo> imageInfos = get();
                    // 更新图片列表
                    for (ImageInfo info : imageInfos) {
                        listModel.addElement(info);
                    }

                    // 如果有图片，默认选中前5个
                    if (!imageInfos.isEmpty()) {
                        int endIndex = Math.min(5, imageInfos.size() - 1);
                        imageList.setSelectionInterval(0, endIndex);
                    }
                } catch (Exception ex) {
                    resultArea.append("处理结果时出错: " + ex.getMessage() + "\n");
                }
            }
        };

        worker.execute();
    }

    private long getImageSize(URL imageURL) throws IOException {
        // 打开连接但不下载完整内容，只获取头部信息，添加请求头
        HttpURLConnection connection = (HttpURLConnection) imageURL.openConnection();
        for (Map.Entry<String, String> header : REQUEST_HEADERS.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        connection.setRequestMethod("HEAD");
        return connection.getContentLengthLong();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        else return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    private void downloadSelectedImages() {
        List<ImageInfo> selectedImages = imageList.getSelectedValuesList();
        if (selectedImages.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先选择至少一张图片");
            return;
        }

        batchDownloadImages(selectedImages);
    }

    private void downloadAllImages() {
        if (listModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有可下载的图片");
            return;
        }

        List<ImageInfo> allImages = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            allImages.add(listModel.getElementAt(i));
        }

        batchDownloadImages(allImages);
    }

    private void batchDownloadImages(List<ImageInfo> imagesToDownload) {
        // 选择保存目录
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = fileChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File saveDirectory = fileChooser.getSelectedFile();
        statusLabel.setText("准备下载...");
        progressBar.setValue(0);
        progressBar.setMaximum(imagesToDownload.size());

        // 启用线程池，限制最大并发数为5
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        StringBuilder errorLog = new StringBuilder();

        for (ImageInfo imageInfo : imagesToDownload) {
            executor.submit(() -> {
                try {
                    String fileName = generateFileName(imageInfo.getUrl());
                    File saveFile = new File(saveDirectory, fileName);

                    downloadImage(imageInfo.getUrl(), saveFile);
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                    errorLog.append("下载失败: ").append(imageInfo.getUrl()).append(" - ").append(e.getMessage()).append("\n");
                }

                // 更新进度条
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(completedCount.get() + failedCount.get());
                    statusLabel.setText("已完成: " + completedCount.get() + "/" + imagesToDownload.size());
                });
            });
        }

        // 关闭线程池
        executor.shutdown();

        // 监控下载完成状态
        SwingWorker<Void, Void> monitor = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                while (!executor.isTerminated()) {
                    Thread.sleep(100);
                }
                return null;
            }

            @Override
            protected void done() {
                statusLabel.setText("下载完成");
                resultArea.append("\n批量下载完成\n");
                resultArea.append("成功:" + completedCount.get() + "张\n");
                resultArea.append("失败: " + failedCount.get() + " 张\n");

                if (failedCount.get() > 0) {
                    resultArea.append("\n下载失败的图片:\n");
                    resultArea.append(errorLog.toString());
                }

                JOptionPane.showMessageDialog(ImageCrawler.this,
                        "批量下载完成\n成功: " + completedCount.get() + " 张\n失败: " + failedCount.get() + " 张",
                        "下载结果", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        monitor.execute();
    }

    private void downloadImage(String imageUrl, File saveFile) throws IOException {
        // 显示下载信息
        SwingUtilities.invokeLater(() -> {
            resultArea.append("开始下载: " + imageUrl + "\n");
        });

        // 下载图片，添加请求头
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        for (Map.Entry<String, String> header : REQUEST_HEADERS.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }

        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(saveFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            // 显示下载完成信息
            SwingUtilities.invokeLater(() -> {
                resultArea.append("已保存: " + saveFile.getAbsolutePath() + "\n");
            });
        }
    }

    private String generateFileName(String url) {
        // 从URL中提取文件名
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        if (fileName.isEmpty() || !fileName.contains(".")) {
            // 如果URL中没有有效的文件名，生成一个唯一的文件名
            fileName = "image_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        }
        return fileName;
    }

    public static void main(String[] args) {
        // 设置Swing应用的外观为系统默认
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 在事件调度线程中创建和显示GUI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ImageCrawler().setVisible(true);
            }
        });
    }

    // 图片信息类
    static class ImageInfo {
        private String url;
        private long size;

        public ImageInfo(String url, long size) {
            this.url = url;
            this.size = size;
        }

        public String getUrl() {
            return url;
        }

        public long getSize() {
            return size;
        }

        @Override
        public String toString() {
            return formatSize(size) + " - " + url;
        }
    }

    // 自定义列表项渲染器
    static class ImageListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof ImageInfo) {
                ImageInfo imageInfo = (ImageInfo) value;
                setText(imageInfo.getUrl() + " (" + formatSize(imageInfo.getSize()) + ")");
            }

            return this;
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            else if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
            else return String.format("%.2f MB", bytes / (1024.0 * 1024));
        }
    }
}
