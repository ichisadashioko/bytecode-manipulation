package io.github.ichisadashioko;

import java.io.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;

import org.objectweb.asm.tree.*;

public final class App {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello World!");

        System.out.println("arguments:");
        for (int i = 0; i < args.length; i++) {
            System.out.println(i + " - " + args[i]);
        }
        System.out.println("end argument.");

        if (args.length < 1) {
            System.out.println("Usage: <input.jar>");
            return;
        }

        String inputFilePath = args[0];

        File input = new File(inputFilePath);

        if (!input.exists()) {
            System.out.println(input.getPath() + " does not exists!");
            return;
        }

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH_mm_ss");

        String outputFilepath = new File(input.getParent(), sdf.format(timestamp) + "_" + input.getName()).getPath();
        System.out.println("output filepath: " + outputFilepath);

        JarFileData jarData = BytecodeManipulation.ParseJarFile(input);

        BytecodeManipulation.DeobfuscateClassNamesInJar(jarData);
        // Map<String, byte[]> classesData = jarData.classesData;
        // Map<String, byte[]> resources = jarData.resources;

        // Map<String, ClassNode> classNodes = jarData.classNodes;

        // Set<String> modifiedClasses = new HashSet<>();
        // Set<String> classSet = classNodes.keySet();

        // // prevent ConcurrentModification
        // String[] classNames = classSet.toArray(new String[classSet.size()]);

        // // rename all classes (add "Class_" prefix)
        // for (String oldClassName : classNames) {
        // if (oldClassName.contains("/")) {
        // continue;
        // }

        // String newClassName = "Class_" + oldClassName;

        // RenameClassArguments renameArguments = new RenameClassArguments();
        // renameArguments.oldClassName = oldClassName;
        // renameArguments.newClassName = newClassName;
        // renameArguments.classNodes = classNodes;

        // ModifiedJarReturns renameClassReturns =
        // BytecodeManipulation.RenameClass(renameArguments);
        // classNodes = renameClassReturns.classNodes;
        // modifiedClasses.addAll(renameClassReturns.modifiedClasses);
        // }

        // File outFile = new File(outputFilePath);

        // ExportJarArguments exportJarArguments = new ExportJarArguments();
        // exportJarArguments.outFile = outFile;
        // exportJarArguments.originalClassData = classesData;
        // exportJarArguments.resources = resources;
        // exportJarArguments.classNodes = classNodes;
        // exportJarArguments.modifiedClasses = modifiedClasses;

        // BytecodeManipulation.ExportJarFile(exportJarArguments);

        // System.out.println("Done!");
    }
}
