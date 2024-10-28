package jdk.tools.jlink.internal;

import jdk.tools.jlink.internal.runtimelink.ResourceDiff;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * A linkable runtime image is a JDK whose jlink tool can create custom images
 * without packaged modules.
 *
 * A linkable runtime image must contain the following files in the jdk.jlink module:
 * 1. one meta-data file for each module.
 * 2. diff files for each module.  The diff files record the difference between
 *    its original content in a packaged module and the corresponding file in the runtime image
 */
public class LinkableRuntimeImage {
    // meta-data files per module stored in a linkable runtime image
    public static final String RESPATH_PATTERN = "jdk/tools/jlink/internal/runtimelink/fs_%s_files";
    // The diff files per module stored in a linkable runtime image
    public static final String DIFF_PATTERN = "jdk/tools/jlink/internal/runtimelink/diff_%s";

    /**
     * Returns true if this JDK runtime image is linkable.
     * i.e. jlink tool in this JDK can create custom images without packaged modules
     */
    static boolean isLinkableRuntime() throws IOException {
        try {
            var resourceName = String.format(DIFF_PATTERN, "java.base");
            var is = LinkableRuntimeImage.class.getModule().getResourceAsStream(resourceName);
            return is != null;
        } catch (IOException e) {
            if (JlinkTask.DEBUG) {
                System.err.println("Failed to get diff pattern resource");
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Returns a JRTArchive of the given module and path whose content is read
     * from the runtime image
     */
    static JRTArchive newArchive(String module, Path path, boolean allowModifiedRuntime) {
        // This is after all other archive types, since user-provided
        // modules might be in any of the above forms and we'd like to
        // support them.
        //
        // For linkable JDK runtimes the modules image includes resource
        // diffs on a per-module bases as part of the jdk.jlink module.
        // See ImageFileCreator.generateJImage() where those are added at
        // JDK build time for linkable JDK runtimes.
        //
        // Here we retrieve the per module difference file, which is
        // potentially empty, from the modules image and pass that on to
        // JRTArchive for further processing. When streaming resources from
        // the archive, the diff is being applied.
        String diffResourceName = String.format(DIFF_PATTERN, module);
        List<ResourceDiff> perModuleDiff = null;
        try (InputStream in = LinkableRuntimeImage.class.getModule().getResourceAsStream(diffResourceName)) {
            perModuleDiff = ResourceDiff.read(in);
        } catch (IOException e) {
            // should this be UncheckedIOException?
            throw new AssertionError("Failure to retrieve resource diff for " +
                    "module " + module, e);
        }
        return new JRTArchive(module, path, !allowModifiedRuntime, perModuleDiff);
    }
}
