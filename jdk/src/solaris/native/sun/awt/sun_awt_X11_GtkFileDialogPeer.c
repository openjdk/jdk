#include <jni.h>
#include <stdio.h>
#include <jni_util.h>
#include <string.h>
#include "gtk2_interface.h"
#include "sun_awt_X11_GtkFileDialogPeer.h"

static JavaVM *jvm;
static GtkWidget *dialog = NULL;

/* To cache some method IDs */
static jmethodID filenameFilterCallbackMethodID = NULL;
static jmethodID setFileInternalMethodID = NULL;

static gboolean filenameFilterCallback(const GtkFileFilterInfo * filter_info, gpointer obj)
{
    JNIEnv *env;
    jclass cx;
    jstring filename;

    env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);

    if (filenameFilterCallbackMethodID == NULL) {
        cx = (*env)->GetObjectClass(env, (jobject) obj);
        if (cx == NULL) {
            JNU_ThrowInternalError(env, "Could not get file filter class");
            return 0;
        }

        filenameFilterCallbackMethodID = (*env)->GetMethodID(env, cx,
                "filenameFilterCallback", "(Ljava/lang/String;)Z");
        if (filenameFilterCallbackMethodID == NULL) {
            JNU_ThrowInternalError(env,
                    "Could not get filenameFilterCallback method id");
            return 0;
        }
    }

    filename = (*env)->NewStringUTF(env, filter_info->filename);

    return (*env)->CallBooleanMethod(env, obj, filenameFilterCallbackMethodID,
            filename);
}

/*
 * Class:     sun_awt_X11_GtkFileDialogPeer
 * Method:    quit
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_X11_GtkFileDialogPeer_quit
(JNIEnv * env, jobject jpeer)
{
    if (dialog != NULL)
    {
        fp_gdk_threads_enter();

        fp_gtk_widget_hide (dialog);
        fp_gtk_widget_destroy (dialog);

        fp_gtk_main_quit ();
        dialog = NULL;

        fp_gdk_threads_leave();
    }
}

/**
 * Convert a GSList to an array of filenames (without the parent folder)
 */
static jobjectArray toFilenamesArray(JNIEnv *env, GSList* list)
{
    jstring str;
    jclass stringCls;
    GSList *iterator;
    jobjectArray array;
    int i;
    char* entry;

    if (NULL == list) {
        return NULL;
    }

    stringCls = (*env)->FindClass(env, "java/lang/String");
    if (stringCls == NULL) {
        JNU_ThrowInternalError(env, "Could not get java.lang.String class");
        return NULL;
    }

    array = (*env)->NewObjectArray(env, fp_gtk_g_slist_length(list), stringCls,
            NULL);
    if (array == NULL) {
        JNU_ThrowInternalError(env, "Could not instantiate array files array");
        return NULL;
    }

    i = 0;
    for (iterator = list; iterator; iterator = iterator->next) {
        entry = (char*) iterator->data;
        entry = strrchr(entry, '/') + 1;
        str = (*env)->NewStringUTF(env, entry);
        (*env)->SetObjectArrayElement(env, array, i, str);
        i++;
    }

    return array;
}

static void handle_response(GtkWidget* aDialog, gint responseId, gpointer obj)
{
    JNIEnv *env;
    char *current_folder;
    GSList *filenames;
    jclass cx;
    jstring jcurrent_folder;
    jobjectArray jfilenames;

    env = (JNIEnv *) JNU_GetEnv(jvm, JNI_VERSION_1_2);
    current_folder = NULL;
    filenames = NULL;

    if (responseId == GTK_RESPONSE_ACCEPT) {
        current_folder = fp_gtk_file_chooser_get_current_folder(
                GTK_FILE_CHOOSER(dialog));
        filenames = fp_gtk_file_chooser_get_filenames(GTK_FILE_CHOOSER(dialog));
    }

    if (setFileInternalMethodID == NULL) {
        cx = (*env)->GetObjectClass(env, (jobject) obj);
        if (cx == NULL) {
            JNU_ThrowInternalError(env, "Could not get GTK peer class");
            return;
        }

        setFileInternalMethodID = (*env)->GetMethodID(env, cx,
                "setFileInternal", "(Ljava/lang/String;[Ljava/lang/String;)V");
        if (setFileInternalMethodID == NULL) {
            JNU_ThrowInternalError(env,
                    "Could not get setFileInternalMethodID method id");
            return;
        }
    }

    jcurrent_folder = (*env)->NewStringUTF(env, current_folder);
    jfilenames = toFilenamesArray(env, filenames);

    (*env)->CallVoidMethod(env, obj, setFileInternalMethodID, jcurrent_folder,
            jfilenames);
    fp_g_free(current_folder);

    Java_sun_awt_X11_GtkFileDialogPeer_quit(NULL, NULL);
}

/*
 * Class:     sun_awt_X11_GtkFileDialogPeer
 * Method:    run
 * Signature: (Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/io/FilenameFilter;Z;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_X11_GtkFileDialogPeer_run(JNIEnv * env, jobject jpeer,
        jstring jtitle, jint mode, jstring jdir, jstring jfile,
        jobject jfilter, jboolean multiple)
{
    GtkFileFilter *filter;

    if (jvm == NULL) {
        (*env)->GetJavaVM(env, &jvm);
    }

    fp_gdk_threads_enter();

    const char *title = (*env)->GetStringUTFChars(env, jtitle, 0);

    if (mode == 1) {
        /* Save action */
        dialog = fp_gtk_file_chooser_dialog_new(title, NULL,
                GTK_FILE_CHOOSER_ACTION_SAVE, GTK_STOCK_CANCEL,
                GTK_RESPONSE_CANCEL, GTK_STOCK_SAVE, GTK_RESPONSE_ACCEPT, NULL);
    }
    else {
        /* Default action OPEN */
        dialog = fp_gtk_file_chooser_dialog_new(title, NULL,
                GTK_FILE_CHOOSER_ACTION_OPEN, GTK_STOCK_CANCEL,
                GTK_RESPONSE_CANCEL, GTK_STOCK_OPEN, GTK_RESPONSE_ACCEPT, NULL);

        /* Set multiple selection mode, that is allowed only in OPEN action */
        if (multiple) {
            fp_gtk_file_chooser_set_select_multiple(GTK_FILE_CHOOSER(dialog),
                    multiple);
        }
    }

    (*env)->ReleaseStringUTFChars(env, jtitle, title);

    /* Set the directory */
    if (jdir != NULL) {
        const char *dir = (*env)->GetStringUTFChars(env, jdir, 0);
        fp_gtk_file_chooser_set_current_folder(GTK_FILE_CHOOSER(dialog), dir);
        (*env)->ReleaseStringUTFChars(env, jdir, dir);
    }

    /* Set the filename */
    if (jfile != NULL) {
        const char *filename = (*env)->GetStringUTFChars(env, jfile, 0);
        fp_gtk_file_chooser_set_filename(GTK_FILE_CHOOSER(dialog), filename);
        (*env)->ReleaseStringUTFChars(env, jfile, filename);
    }

    /* Set the file filter */
    if (jfilter != NULL) {
        filter = fp_gtk_file_filter_new();
        fp_gtk_file_filter_add_custom(filter, GTK_FILE_FILTER_FILENAME,
                filenameFilterCallback, jpeer, NULL);
        fp_gtk_file_chooser_set_filter(GTK_FILE_CHOOSER(dialog), filter);
    }

    /* Other Properties */
    if (fp_gtk_check_version(2, 8, 0) == NULL) {
        fp_gtk_file_chooser_set_do_overwrite_confirmation(GTK_FILE_CHOOSER(
                dialog), TRUE);
    }

    fp_g_signal_connect(G_OBJECT(dialog), "response", G_CALLBACK(
            handle_response), jpeer);
    fp_gtk_widget_show(dialog);

    fp_gtk_main();
    fp_gdk_threads_leave();
}
