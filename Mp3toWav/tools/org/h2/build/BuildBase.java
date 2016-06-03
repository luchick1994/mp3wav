package org.h2.build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class BuildBase {


    public static class StringList extends ArrayList<String> {

        private static final long serialVersionUID = 1L;

        StringList(String... args) {
            super();
            addAll(Arrays.asList(args));
        }


        public StringList plus(String...args) {
            StringList newList = new StringList();
            newList.addAll(this);
            newList.addAll(Arrays.asList(args));
            return newList;
        }


        public String[] array() {
            String[] list = new String[size()];
            for (int i = 0; i < size(); i++) {
                list[i] = get(i);
            }
            return list;
        }

    }


    public static class FileList extends ArrayList<File> {

        private static final long serialVersionUID = 1L;


        public FileList exclude(String pattern) {
            return filter(false, pattern);
        }


        public FileList keep(String pattern) {
            return filter(true, pattern);
        }


        private FileList filter(boolean keep, String pattern) {
            boolean start = false;
            if (pattern.endsWith("*")) {
                pattern = pattern.substring(0, pattern.length() - 1);
                start = true;
            } else if (pattern.startsWith("*")) {
                pattern = pattern.substring(1);
            }
            if (pattern.indexOf('*') >= 0) {
                throw new RuntimeException("Unsupported pattern, may only start or end with *:" + pattern);
            }
            pattern = replaceAll(pattern, "/", File.separator);
            FileList list = new FileList();
            for (File f : this) {
                String path = f.getPath();
                boolean match = start ? path.startsWith(pattern) : path.endsWith(pattern);
                if (match == keep) {
                    list.add(f);
                }
            }
            return list;
        }

    }


    protected PrintStream sysOut = System.out;


    protected boolean quiet;

 
    protected void run(String... args) {
        long time = System.currentTimeMillis();
        if (args.length == 0) {
            all();
        } else {
            for (String a : args) {
                if ("-quiet".equals(a)) {
                    quiet = true;
                } else if (a.startsWith("-D")) {
                    String value;
                    String key = a.substring(2);
                    int valueIndex = key.indexOf('=');
                    if (valueIndex >= 0) {
                        value = key.substring(valueIndex + 1);
                        key = key.substring(0, valueIndex);
                    } else {
                        value = "true";
                    }
                    System.setProperty(key, value);
                } else {
                    Method m = null;
                    try {
                        m = getClass().getMethod(a);
                    } catch (Exception e) {
                        sysOut.println("Unknown target: " + a);
                        projectHelp();
                        break;
                    }
                    println("Target: " + a);
                    invoke(m, this, new Object[0]);
                }
            }
        }
        println("Done in " + (System.currentTimeMillis() - time) + " ms");
    }

    private Object invoke(Method m, Object instance, Object[] args) {
        try {
            try {
                return m.invoke(instance, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        } catch (Error e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    protected void all() {
        projectHelp();
    }


    protected void beep() {
        sysOut.print("\007");
        sysOut.flush();
    }

    
    protected void projectHelp() {
        Method[] methods = getClass().getDeclaredMethods();
        Arrays.sort(methods, new Comparator<Method>() {
            public int compare(Method a, Method b) {
                return a.getName().compareTo(b.getName());
            }
        });
        sysOut.println("Targets:");
        for (Method m : methods) {
            int mod = m.getModifiers();
            if (!Modifier.isStatic(mod) && Modifier.isPublic(mod) && m.getParameterTypes().length == 0) {
                sysOut.println(m.getName());
            }
        }
        sysOut.println();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0;
    }


    protected int execScript(String script, StringList args) {
        if (isWindows()) {
            script = script + ".bat";
        }
        return exec(script, args);
    }


    protected int exec(String command, StringList args) {
        try {
            print(command);
            StringList cmd = new StringList();
            cmd = cmd.plus(command);
            if (args != null) {
                for (String a : args) {
                    print(" " + a);
                }
                cmd.addAll(args);
            }
            println("");
            Process p = Runtime.getRuntime().exec(cmd.array());
            copyInThread(p.getInputStream(), quiet ? null : sysOut);
            copyInThread(p.getErrorStream(), quiet ? null : sysOut);
            p.waitFor();
            return p.exitValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void copyInThread(final InputStream in, final OutputStream out) {
        new Thread() {
            public void run() {
                try {
                    while (true) {
                        int x = in.read();
                        if (x < 0) {
                            return;
                        }
                        if (out != null) {
                            out.write(x);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } .start();
    }


    protected String getStaticField(String className, String fieldName) {
        try {
            Class< ? > clazz = Class.forName(className);
            Field field = clazz.getField(fieldName);
            return field.get(null).toString();
        } catch (Exception e) {
            throw new RuntimeException("Can not read field " + className + "." + fieldName, e);
        }
    }


    protected String getStaticValue(String className, String methodName) {
        try {
            Class< ? > clazz = Class.forName(className);
            Method method = clazz.getMethod(methodName);
            return method.invoke(null).toString();
        } catch (Exception e) {
            throw new RuntimeException("Can not read value " + className + "." + methodName + "()", e);
        }
    }


    protected void copy(String targetDir, FileList files, String baseDir) {
        File target = new File(targetDir);
        File base = new File(baseDir);
        println("Copying " + files.size() + " files to " + target.getPath());
        String basePath = base.getPath();
        for (File f : files) {
            File t = new File(target, removeBase(basePath, f.getPath()));
            byte[] data = readFile(f);
            mkdirs(t.getParentFile());
            writeFile(t, data);
        }
    }

    private PrintStream filter(PrintStream out, final String[] exclude) {
        return new PrintStream(new FilterOutputStream(out) {
            private ByteArrayOutputStream buff = new ByteArrayOutputStream();

            public void write(byte[] b) throws IOException {
                write(b, 0, b.length);
            }

            public void write(byte[] b, int off, int len) throws IOException {
                for (int i = off; i < len; i++) {
                    write(b[i]);
                }
            }

            public void write(byte b) throws IOException {
                buff.write(b);
                if (b == '\n') {
                    byte[] data = buff.toByteArray();
                    String line = new String(data, "UTF-8");
                    boolean print = true;
                    for (String l : exclude) {
                        if (line.startsWith(l)) {
                            print = false;
                            break;
                        }
                    }
                    if (print) {
                        out.write(data);
                    }
                    buff.reset();
                }
            }

            public void close() throws IOException {
                write('\n');
            }
        });
    }


    protected void javadoc(String...args) {
        int result;
        PrintStream old = System.out;
        try {
            println("Javadoc");
            if (quiet) {
                System.setOut(filter(System.out, new String[] {
                        "Loading source files for package",
                        "Constructing Javadoc information",
                        "Generating ",
                        "Standard Doclet",
                        "Building "
                }));
            }
            Class< ? > clazz = Class.forName("com.sun.tools.javadoc.Main");
            Method execute = clazz.getMethod("execute", String[].class);
            result = (Integer) invoke(execute, null, new Object[] { args });
        } catch (Exception e) {
            result = exec("javadoc", args(args));
        } finally {
            System.setOut(old);
        }
        if (result != 0) {
            throw new RuntimeException("An error occurred, result=" + result);
        }
    }

    private String convertBytesToString(byte[] value) {
        StringBuilder buff = new StringBuilder(value.length * 2);
        for (byte c : value) {
            int x = c & 0xff;
            buff.append(Integer.toString(x >> 4, 16)).
                append(Integer.toString(x & 0xf, 16));
        }
        return buff.toString();
    }

  
    protected String getSHA1(byte[] data) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
            return convertBytesToString(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

  
    protected void download(String target, String fileURL, String sha1Checksum) {
        File targetFile = new File(target);
        if (targetFile.exists()) {
            return;
        }
        mkdirs(targetFile.getAbsoluteFile().getParentFile());
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        try {
            println("Downloading " + fileURL);
            URL url = new URL(fileURL);
            InputStream in = new BufferedInputStream(url.openStream());
            long last = System.currentTimeMillis();
            int len = 0;
            while (true) {
                long now = System.currentTimeMillis();
                if (now > last + 1000) {
                    println("Downloaded " + len + " bytes");
                    last = now;
                }
                int x = in.read();
                len++;
                if (x < 0) {
                    break;
                }
                buff.write(x);
            }
            in.close();
        } catch (IOException e) {
            throw new RuntimeException("Error downloading", e);
        }
        byte[] data = buff.toByteArray();
        String got = getSHA1(data);
        if (sha1Checksum == null) {
            println("SHA1 checksum: " + got);
        } else {
            if (!got.equals(sha1Checksum)) {
                throw new RuntimeException("SHA1 checksum mismatch; got: " + got);
            }
        }
        writeFile(targetFile, data);
    }


    protected FileList files(String dir) {
        FileList list = new FileList();
        addFiles(list, new File(dir));
        return list;
    }


    protected StringList args(String...args) {
        return new StringList(args);
    }

    private void addFiles(FileList list, File file) {
        if (file.getName().startsWith(".svn")) {
            // ignore
        } else if (file.isDirectory()) {
            String path = file.getPath();
            for (String fileName : file.list()) {
                addFiles(list, new File(path, fileName));
            }
        } else {
            list.add(file);
        }
    }

    private String removeBase(String basePath, String path) {
        if (path.startsWith(basePath)) {
            path = path.substring(basePath.length());
        }
        path = path.replace('\\', '/');
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    
    public static void writeFile(File file, byte[] data) {
        try {
            RandomAccessFile ra = new RandomAccessFile(file, "rw");
            ra.write(data);
            ra.setLength(data.length);
            ra.close();
        } catch (IOException e) {
            throw new RuntimeException("Error writing to file " + file, e);
        }
    }


    public static byte[] readFile(File file) {
        try {
            RandomAccessFile ra = new RandomAccessFile(file, "r");
            long len = ra.length();
            if (len >= Integer.MAX_VALUE) {
                throw new RuntimeException("File " + file.getPath() + " is too large");
            }
            byte[] buffer = new byte[(int) len];
            ra.readFully(buffer);
            ra.close();
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException("Error reading from file " + file, e);
        }
    }


    String getSuffix(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx);
    }

 
    protected long jar(String destFile, FileList files, String basePath) {
        long kb = zipOrJar(destFile, files, basePath, false, false, true);
        println("Jar " + destFile + " (" + kb + " KB)");
        return kb;
    }


    protected void zip(String destFile, FileList files, String basePath, boolean storeOnly, boolean sortBySuffix) {
        long kb = zipOrJar(destFile, files, basePath, storeOnly, sortBySuffix, false);
        println("Zip " + destFile + " (" + kb + " KB)");
    }

    private long zipOrJar(String destFile, FileList files, String basePath, boolean storeOnly, boolean sortBySuffix, boolean jar) {
        if (sortBySuffix) {
            Collections.sort(files, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    String p1 = f1.getPath();
                    String p2 = f2.getPath();
                    int comp = getSuffix(p1).compareTo(getSuffix(p2));
                    if (comp == 0) {
                        comp = p1.compareTo(p2);
                    }
                    return comp;
                }
            });
        }
        mkdirs(new File(destFile).getAbsoluteFile().getParentFile());
        // normalize the path (replace / with \ if required)
        basePath = new File(basePath).getPath();
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(destFile));
            ZipOutputStream zipOut;
            if (jar) {
                zipOut = new JarOutputStream(out);
            } else {
                zipOut = new ZipOutputStream(out);
            }
            if (storeOnly) {
                zipOut.setMethod(ZipOutputStream.STORED);
            }
            zipOut.setLevel(Deflater.BEST_COMPRESSION);
            for (File file : files) {
                String fileName = file.getPath();
                String entryName = removeBase(basePath, fileName);
                byte[] data = readFile(file);
                ZipEntry entry = new ZipEntry(entryName);
                CRC32 crc = new CRC32();
                crc.update(data);
                entry.setSize(file.length());
                entry.setCrc(crc.getValue());
                zipOut.putNextEntry(entry);
                zipOut.write(data);
                zipOut.closeEntry();
            }
            zipOut.closeEntry();
            zipOut.close();
            return new File(destFile).length() / 1024;
        } catch (IOException e) {
            throw new RuntimeException("Error creating file " + destFile, e);
        }
    }

  
    protected String getJavaSpecVersion() {
        return System.getProperty("java.specification.version");
    }

    private List<String> getPaths(FileList files) {
        StringList list = new StringList();
        for (File f : files) {
            list.add(f.getPath());
        }
        return list;
    }

  
    protected void javac(StringList args, FileList files) {
        println("Compiling " + files.size() + " classes");
        StringList params = new StringList();
        params.addAll(args);
        params.addAll(getPaths(files.keep(".java")));
        String[] array = params.array();
        int result;
        PrintStream old = System.err;
        try {
            Class< ? > clazz = Class.forName("com.sun.tools.javac.Main");
            if (quiet) {
                System.setErr(filter(System.err, new String[] {
                        "Note:"
                }));
            }
            Method compile = clazz.getMethod("compile", new Class< ? >[] { String[].class });
            Object instance = clazz.newInstance();
            result = (Integer) invoke(compile, instance, new Object[] { array });
        } catch (Exception e) {
            e.printStackTrace();
            result = exec("javac", new StringList(array));
        } finally {
            System.setErr(old);
        }
        if (result != 0) {
            throw new RuntimeException("An error occurred");
        }
    }

    protected void java(String className, StringList args) {
        println("Running " + className);
        String[] array = args == null ? new String[0] : args.array();
        try {
            Method main = Class.forName(className).getMethod("main", String[].class);
            invoke(main, null, new Object[] { array });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    protected void mkdir(String dir) {
        File f = new File(dir);
        if (f.exists()) {
            if (f.isFile()) {
                throw new RuntimeException("Can not create directory " + dir + " because a file with this name exists");
            }
        } else {
            mkdirs(f);
        }
    }

    private void mkdirs(File f) {
        if (!f.exists()) {
            if (!f.mkdirs()) {
                throw new RuntimeException("Can not create directory " + f.getAbsolutePath());
            }
        }
    }


    protected void delete(String dir) {
        println("Deleting " + dir);
        delete(new File(dir));
    }


    protected void delete(FileList files) {
        for (File f : files) {
            delete(f);
        }
    }

    private void delete(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                String path = file.getPath();
                for (String fileName : file.list()) {
                    delete(new File(path, fileName));
                }
            }
            if (!file.delete()) {
                throw new RuntimeException("Can not delete " + file.getPath());
            }
        }
    }


    protected static String replaceAll(String s, String before, String after) {
        int index = 0;
        while (true) {
            int next = s.indexOf(before, index);
            if (next < 0) {
                return s;
            }
            s = s.substring(0, next) + after + s.substring(next + before.length());
            index = next + after.length();
        }
    }

    protected void println(String s) {
        if (!quiet) {
            sysOut.println(s);
        }
    }

    protected void print(String s) {
        if (!quiet) {
            sysOut.print(s);
        }
    }

}
