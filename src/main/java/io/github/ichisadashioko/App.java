package io.github.ichisadashioko;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.*;
import java.util.zip.*;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

import me.coley.recaf.bytecode.ClassUtil;
import me.coley.recaf.workspace.ClassesMap;
import me.coley.recaf.workspace.InputBuilder;
import me.coley.recaf.workspace.ResourcesMap;

/**
 * Hello world!
 */
public final class App {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello World!");

        File input = new File("E:\\jar-remapper\\heroes-lore-wind-of-solti.jar");

        Map<String, byte[]> classesData = new HashMap<>();
        Map<String, ClassNode> classNodes = new HashMap<>();

        Map<String, byte[]> resources = new HashMap<>();

        ZipFile zipFile = new ZipFile(input);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }

            InputStream is = zipFile.getInputStream(entry);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;

            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }

            byte[] value = os.toByteArray();
            String name = entry.getName();
            // System.out.println(name);

            if (name.endsWith(".class")) {
                classesData.put(name, value);

                ClassReader cr = new ClassReader(value);
                ClassNode cn = new ClassNode();
                cr.accept(cn, ClassReader.EXPAND_FRAMES);
                classNodes.put(name, cn);
            } else {
                resources.put(name, value);
            }
        }

        zipFile.close();

        String oldClassName = "a";
        String newClassName = "Class_" + oldClassName;

        Set<String> referenced = new HashSet<>();
        Map<String, ClassNode> updateMap = new HashMap<>();

        for (ClassNode cn : classNodes.values()) {
            // System.out.println(String.format("cn.name: %s", cn.name));
            ClassNode updated = new ClassNode();
            cn.accept(new ClassRemapper(updated, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (internalName.equals(oldClassName)) {
                        referenced.add(cn.name);
                        // System.out.println(String.format("internalName: %s, cn.name: %s",
                        // internalName, cn.name));
                        return newClassName;
                    }
                    return super.map(internalName);
                }
            }));

            if (referenced.contains(cn.name)) {
                updateMap.put(cn.name, updated);
            }
        }

        for (Entry<String, ClassNode> e : updateMap.entrySet()) {
            // String name = e.getKey();
            ClassNode updated = e.getValue();

            if (updated.name.equals(oldClassName)) {
                classNodes.put(newClassName, updated);
            } else {
                classNodes.put(updated.name, updated);
            }
        }

        ClassNode node = classNodes.get(newClassName);
        if (node == null) {
            throw new RuntimeException();
        }

        // update inner classes
        for (InnerClassNode innerNode : node.innerClasses) {
            String inner = innerNode.name;

            if (inner.equals(oldClassName)) {
                continue;
            }

            if (inner.equals(newClassName)) {
                continue;
            }

            // Ensure the class exist
            if (!classNodes.containsKey(inner)) {
                continue;
            }

            String newInnerClassName = null;
            if (innerNode.outerName != null && innerNode.innerName != null) {
                if (!innerNode.outerName.equals(newClassName)) {
                    continue;
                }

                newInnerClassName = newClassName + "$" + innerNode.innerName;
            } else {
                int splitIndex = inner.lastIndexOf("$");
                String innerHost = inner.substring(0, splitIndex);
                if (!innerHost.equals(oldClassName) && !innerHost.equals(newClassName)) {
                    continue;
                }

                String innerName = inner.substring(splitIndex + 1);

                if (newClassName.startsWith(inner) && inner.length() < newClassName.length()) {
                    continue;
                }

                newInnerClassName = newClassName + "$" + innerName;
            }

            if(newInnerClassName != null && !inner.equals(newInnerClassName)){
                System.out.println(String.format("newInnerClassName: %s, inner: %s", newInnerClassName, inner));
                // TODO call rename(classNodes.get(inner), inner, innerNewClassName)
            }
        }

        // InputBuilder builder = new InputBuilder(input);

        // Set<String> classes = builder.getClasses();
        // Set<String> resources = builder.getResources();

        // ClassesMap classMap = new ClassesMap();
        // classMap.putAllRaw(builder.getClassContent());

        // // map of parent to children names
        // Map<String, Set<String>> descendents = new HashMap<>();
        // descendents.clear();
        // for (ClassNode cn : classMap.values()) {
        // descendents.computeIfAbsent(cn.superName, k -> new HashSet<>()).add(cn.name);
        // for (String inter : cn.interfaces) {
        // descendents.computeIfAbsent(inter, k -> new HashSet<>()).add(cn.name);
        // }
        // }

        // String[] classNames = classes.toArray(new String[classes.size()]);
        // for (String c : classNames) {
        // if (classMap.containsKey(c)) {
        // ClassNode cn = classMap.get(c);

        // if (cn.name.contains("/")) {
        // continue;
        // }

        // String newClassName = "Class_" + cn.name;

        // System.out.println(String.format("%s -> %s", cn.name, newClassName));
        // renameClass(cn, classMap, classes, cn.name, newClassName);
        // } else {
        // System.out.println(String.format("Class %s has been removed!", c));
        // }
        // }
    }

    // public static byte[] getClassBytes(ClassNode cn) throws Exception {
    // ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    // }

    public static void exportJar(File file, Set<String> dirtyClasses, Set<String> classes, ClassesMap classMap,
            Set<String> resources, ResourcesMap resourceMap) throws Exception {
        Map<String, byte[]> contents = new TreeMap<>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                // packages are higher valued than default-package entries
                if (o1.contains("/") && !o2.contains("/")) {
                    return -1;
                } else if (!o1.contains("/") && o2.contains("/")) {
                    return 1;
                } else {
                    // standard name comparision
                    return o1.compareTo(o2);
                }
            }
        });

        // write classes
        Set<String> modified = dirtyClasses;
        for (String name : classes) {
            // Export if the file has been modified.
            // We know if it is modifed if it has a history or is marked as dirty.
            if (modified.contains(name)) {
                byte[] data = ClassUtil.getBytes(classMap.get(name));
                contents.put(name + ".class", data);
                continue;
            }

            // Otherwise we don't even have to have ASM regenerate the bytecode. We can just
            // plop the exact original file back into the output.
            // This is greate for editing one file in large system.

            // Or we can be here because the export failed, which sucks, but is better than
            // not exporting anything at all. At least the user will be notified in the
            // console.
            byte[] data = classMap.getRaw(name);
            contents.put(name + ".class", data);
        }

        for (String name : resources) {
            byte[] data = resourceMap.get(name);
            contents.put(name, data);
        }

        JarOutputStream output = new JarOutputStream(new FileOutputStream(file));
        Set<String> dirsVisited = new HashSet<>();
        // contents is iterated in sorted order (because type is TreeMap)
        // this allows us to insert directory entries before file entries of that
        // directory occur
        for (Entry<String, byte[]> entry : contents.entrySet()) {
            String key = entry.getKey();
            // Write directories for upcoming entries if necessary
            // - Ugly, but does the job
            if (key.contains("/")) {
                // Record directories
                String parent = key;
                List<String> toAdd = new ArrayList<>();
                do {
                    parent = parent.substring(0, parent.lastIndexOf("/"));
                    if (!dirsVisited.contains(parent)) {
                        dirsVisited.add(parent);
                        toAdd.add(0, parent + "/");
                    } else {
                        break;
                    }
                } while (parent.contains("/"));
                // Put directories in order of depth
                for (String dir : toAdd) {
                    output.putNextEntry(new JarEntry(dir));
                    output.closeEntry();
                }
            }

            // write entry content
            output.putNextEntry(new JarEntry(key));
            output.write(entry.getValue());
            output.closeEntry();
        }

        output.close();
    }

    public static void renameClass(ClassNode eventNode, ClassesMap classMap, Set<String> classes, String nameOriginal,
            String nameRenamed) {

        Set<String> referenced = new HashSet<>();
        Map<String, ClassNode> updatedMap = new HashMap<>();
        referenced.add(nameRenamed);
        // replace references in all classes
        for (ClassNode cn : classMap.values()) {
            ClassNode updated = new ClassNode();
            cn.accept(new ClassRemapper(updated, new Remapper() {
                @Override
                public String map(String internalName) {
                    if (internalName.equals(nameOriginal)) {
                        // mark classes that have referenced the renamed class.
                        referenced.add(cn.name);
                        return nameRenamed;
                    }
                    return super.map(internalName);
                }
            }));
            if (referenced.contains(cn.name)) {
                updatedMap.put(cn.name, updated);
            }
        }
        // Update all classes with references to the renamed class.
        for (Entry<String, ClassNode> e : updatedMap.entrySet()) {
            // Remove old ClassNode
            classMap.removeRaw(e.getKey());
            // Put & update new ClassNode
            ClassNode updated = e.getValue();
            if (updated.name.equals(nameRenamed) || updated.name.equals(nameOriginal)) {
                // Update the renamed class (itself)
                classMap.put(nameRenamed, updated);
            } else {
                // Update the class that contains references to the renamed class
                classMap.put(updated.name, updated);
                classMap.remove(updated.name);
            }

        }
        // Get updated node
        ClassNode node = classMap.get(nameRenamed);
        if (node == null) {
            throw new RuntimeException(
                    "Failed to fetch updated ClassNode for remapped class: " + nameOriginal + " -> " + nameRenamed);
        }
        // update inner classes
        for (InnerClassNode innerNode : node.innerClasses) {
            String inner = innerNode.name;
            // ASM gives inner-classes a constant of themselves, copied from
            // their parent.
            // So skip self-referencing values.
            if (inner.equals(nameOriginal)) {
                continue;
            }
            // And skip self-referneces for renamed parents.
            if (inner.equals(nameRenamed)) {
                continue;
            }

            // Ensure the class exists. Can contain inner classes of other nodes
            // that may not exist (such as Map$Entry)
            if (!classes.contains(inner)) {
                continue;
            }
            // New name of inner, to be defined next.
            String innerNew = null;
            // Check if inner/outer names exist. This will work for
            // non-anonymous classes.
            if (innerNode.outerName != null && innerNode.innerName != null) {
                // Verify that this should be renamed. Don't want to mess with
                // it if it is defined by another class.
                if (!innerNode.outerName.equals(nameRenamed)) {
                    continue;
                }
                innerNew = nameRenamed + "$" + innerNode.innerName;
            } else {
                // Deal with anonymous class.
                //
                // Inner ----------- Host ----------- Should rename? -- Case
                // Orig$1 ---------- Renmaed$Sub ---- NO -------------- 1
                // Orig$Sub$1 ------ Renmaed$Sub ---- YES ------------- 2
                // Renmaed$Sub ----- Renmaed$Sub$1 -- NO -------------- 3
                // Renmaed$1$1 ----- Renmaed$Sub$1 -- NO -------------- 4
                //
                int splitIndex = inner.lastIndexOf("$");
                // Verify the name of the inner class does not denote that it
                // has been defined in another class. This is case 4.
                String innerHost = inner.substring(0, splitIndex);
                if (!innerHost.equals(nameOriginal) && !innerHost.equals(nameRenamed)) {
                    continue;
                }
                String innerName = inner.substring(splitIndex + 1);
                // Account for case 3, where the inner is sorta an outer class.
                if (nameRenamed.startsWith(inner) && inner.length() < nameRenamed.length()) {
                    continue;
                }
                innerNew = nameRenamed + "$" + innerName;
            }
            // Send rename event if innerName is updated.
            if (innerNew != null && !inner.equals(innerNew)) {
                // TODO convert to recursive function call
                // Bus.post(new ClassRenameEvent(getClass(inner), inner, innerNew));
                // Bus.post(new ClassReloadEvent(inner, innerNew));
                renameClass(classMap.get(inner), classMap, classes, inner, innerNew);
            }
        }
        // add new name
        classes.add(nameRenamed);
        // remove original name from input sets
        classMap.remove(nameOriginal);
        // dirtyClasses.remove(nameOriginal);
        // history.remove(nameOriginal);
        classes.remove(nameOriginal);
        System.out.println("Rename " + nameOriginal + " -> " + nameRenamed);
    }
}
