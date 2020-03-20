package io.github.ichisadashioko;

import java.io.*;
import java.util.*;

import org.objectweb.asm.tree.*;

public final class App {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello World!");

        String inputFilePath = "input.jar";
        String outputFilePath = "output.jar";

        File input = new File(inputFilePath);

        JarFileData jarData = BytecodeManipulation.ParseJarFile(input);

        Map<String, byte[]> classesData = jarData.classesData;
        Map<String, byte[]> resources = jarData.resources;

        Map<String, ClassNode> classNodes = jarData.classNodes;

        Set<String> modifiedClasses = new HashSet<>();
        Set<String> classSet = classNodes.keySet();

        // prevent ConcurrentModification
        String[] classNames = classSet.toArray(new String[classSet.size()]);

        // rename all classes (add "Class_" prefix)
        for (String oldClassName : classNames) {
            if (oldClassName.contains("/")) {
                continue;
            }

            String newClassName = "Class_" + oldClassName;

            RenameClassArguments renameArguments = new RenameClassArguments();
            renameArguments.oldClassName = oldClassName;
            renameArguments.newClassName = newClassName;
            renameArguments.classNodes = classNodes;

            ModifiedJarReturns renameClassReturns = BytecodeManipulation.RenameClass(renameArguments);
            classNodes = renameClassReturns.classNodes;
            modifiedClasses.addAll(renameClassReturns.modifiedClasses);
        }

        File outFile = new File(outputFilePath);

        ExportJarArguments exportJarArguments = new ExportJarArguments();
        exportJarArguments.outFile = outFile;
        exportJarArguments.originalClassData = classesData;
        exportJarArguments.resources = resources;
        exportJarArguments.classNodes = classNodes;
        exportJarArguments.modifiedClasses = modifiedClasses;

        BytecodeManipulation.ExportJarFile(exportJarArguments);

        System.out.println("Done!");
    }
}
