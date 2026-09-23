package com.coyotai.education.student;

import com.coyotai.education.common.BusinessRuleException;
import com.coyotai.education.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

@Service
public class StudentPhotoStorage {
    private final Path root;
    public StudentPhotoStorage(@Value("${storage.student-photos.directory}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public byte[] validate(MultipartFile file) {
        if (file.isEmpty()) throw new BusinessRuleException("Choose a non-empty JPEG or PNG photo");
        if (file.getSize() > 5 * 1024 * 1024) throw new BusinessRuleException("Photo must be 5 MB or smaller");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessRuleException("Upload a JPEG or PNG photo");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw new BusinessRuleException("Upload a JPEG or PNG photo");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 20_000_000)
                    throw new BusinessRuleException("Photo must be no larger than 20 megapixels");
                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0, 1600.0 / Math.max(width, height));
                BufferedImage normalized = new BufferedImage(Math.max(1, (int)(width * scale)), Math.max(1, (int)(height * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = normalized.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
                    graphics.drawImage(source, 0, 0, normalized.getWidth(), normalized.getHeight(), null);
                } finally { graphics.dispose(); }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(normalized, "jpg", bytes);
                return bytes.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new BusinessRuleException("The photo could not be read; choose a valid JPEG or PNG"); }
    }

    public String store(byte[] image, String previous) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("Photo writes require a transaction");
        String name = UUID.randomUUID() + ".jpg";
        Path target = resolve(name);
        try {
            Files.createDirectories(root);
            Files.write(target, image, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            remove(name);
            throw new BusinessRuleException("Unable to store the photo. Check the configured photo folder and write permissions.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) remove(previous);
                else remove(name);
            }
        });
        return name;
    }

    public Resource load(String name) {
        if (name == null) throw ResourceNotFoundException.of("Student photo", "missing");
        Path file = resolve(name);
        if (!Files.isRegularFile(file)) throw ResourceNotFoundException.of("Student photo", "missing");
        return new FileSystemResource(file);
    }

    private Path resolve(String name) {
        if (name == null || !name.matches("[a-f0-9-]{36}\\.jpg")) throw new BusinessRuleException("Invalid photo filename");
        Path result = root.resolve(name).normalize();
        if (!result.startsWith(root)) throw new BusinessRuleException("Invalid photo filename");
        return result;
    }

    private void remove(String name) {
        if (name == null) return;
        try { Files.deleteIfExists(resolve(name)); }
        catch (IOException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not remove unused student photo {}", name); }
    }
}
