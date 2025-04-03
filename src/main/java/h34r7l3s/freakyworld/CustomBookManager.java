package h34r7l3s.freakyworld;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CustomBookManager {

    private static File bookFile;

    public static void initialize(FreakyWorld plugin) {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            bookFile = new File(dataFolder, "book.txt");
            if (!bookFile.exists()) {
                bookFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ItemStack createCustomBook(String initialText) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();

        if (bookMeta != null) {
            bookMeta.setTitle("FreakyWorlds Erben");
            bookMeta.setAuthor("Server");
            bookMeta.setPages("");
            bookMeta.addPage(initialText);

            book.setItemMeta(bookMeta);
        }

        return book;
    }

    public static boolean isCustomBook(ItemStack item) {
        return item != null && item.getType() == Material.WRITABLE_BOOK && item.hasItemMeta() && item.getItemMeta() instanceof BookMeta;
    }

    public static void openBook(Player player, ItemStack book) {
        if (isCustomBook(book)) {
            player.openBook(book);
        }
    }

    public static void addNewPage(Player player, String pageText) {
        ItemStack book = player.getInventory().getItemInMainHand();
        if (isCustomBook(book)) {
            BookMeta bookMeta = (BookMeta) book.getItemMeta();

            if (bookMeta != null) {
                bookMeta.addPage(pageText);
                book.setItemMeta(bookMeta);
            }

            saveBookContent(book); // Speichert das Buch nach der Änderung
        }
    }

    private static void saveBookContent(ItemStack book) {
        try {
            BookMeta bookMeta = (BookMeta) book.getItemMeta();
            if (bookMeta != null) {
                List<String> pages = bookMeta.getPages();
                try (PrintWriter out = new PrintWriter(new FileWriter(bookFile))) {
                    for (String page : pages) {
                        out.println(page);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ItemStack loadBookContent() {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();

        if (bookMeta != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(bookFile))) {
                String line;
                List<String> pages = new ArrayList<>();
                while ((line = br.readLine()) != null) {
                    pages.add(line);
                }
                bookMeta.setPages(pages);
                book.setItemMeta(bookMeta);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return book;
    }
}
