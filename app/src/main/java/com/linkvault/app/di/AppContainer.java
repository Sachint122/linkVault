package com.linkvault.app.di;

import android.content.Context;

import com.linkvault.app.ai.AIQueueManager;
import com.linkvault.app.ai.InferenceQueue;
import com.linkvault.app.ai.LocalAiManager;
import com.linkvault.app.data.backup.BackupEngine;
import com.linkvault.app.data.backup.RestoreEngine;
import com.linkvault.app.data.json.AutoCatCodec;
import com.linkvault.app.data.json.CategoryCodec;
import com.linkvault.app.data.json.FileCodec;
import com.linkvault.app.data.json.LinkCodec;
import com.linkvault.app.data.repo.AutoCatRepository;
import com.linkvault.app.data.repo.CategoryRepository;
import com.linkvault.app.data.repo.FileRepository;
import com.linkvault.app.data.repo.IndexRepository;
import com.linkvault.app.data.repo.RecordRepository;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.SafeWriteEngine;
import com.linkvault.app.data.saf.StoragePermissionManager;

/**
 * Manual DI container. Repositories are recreated whenever the storage
 * folder changes ({@link #onStorageFolderChanged()}).
 */
public class AppContainer {

    private final Context appContext;
    private final StoragePermissionManager permissionManager;
    private final LinkCodec linkCodec = new LinkCodec();
    private final CategoryCodec categoryCodec = new CategoryCodec();
    private final FileCodec fileCodec = new FileCodec();
    private final AutoCatCodec autoCatCodec = new AutoCatCodec();

    private volatile DocumentTreeClient treeClient;
    private volatile SafeWriteEngine writeEngine;
    private volatile CategoryRepository categoryRepository;
    private volatile RecordRepository recordRepository;
    private volatile FileRepository fileRepository;
    private volatile IndexRepository indexRepository;
    private volatile AutoCatRepository autoCatRepository;
    private volatile BackupEngine backupEngine;
    private volatile RestoreEngine restoreEngine;
    /** Internal-storage services — independent of the SAF folder. */
    private volatile LocalAiManager localAiManager;
    private volatile AIQueueManager aiQueueManager;
    /**
     * Single shared inference queue (sidebar writing assistant + link
     * summarization) in front of the one model instance.
     */
    private volatile InferenceQueue inferenceQueue;

    public AppContainer(Context context) {
        this.appContext = context.getApplicationContext();
        this.permissionManager = new StoragePermissionManager(appContext);
    }

    public Context appContext() {
        return appContext;
    }

    public StoragePermissionManager permissionManager() {
        return permissionManager;
    }

    public LinkCodec linkCodec() {
        return linkCodec;
    }

    public CategoryCodec categoryCodec() {
        return categoryCodec;
    }

    public FileCodec fileCodec() {
        return fileCodec;
    }

    public synchronized DocumentTreeClient treeClient() {
        if (treeClient == null) {
            android.net.Uri treeUri = permissionManager.getTreeUri();
            if (treeUri == null) {
                throw new IllegalStateException("Storage folder not selected yet");
            }
            treeClient = new DocumentTreeClient(appContext, treeUri);
        }
        return treeClient;
    }

    public synchronized SafeWriteEngine writeEngine() {
        if (writeEngine == null) {
            writeEngine = new SafeWriteEngine(treeClient());
        }
        return writeEngine;
    }

    public synchronized CategoryRepository categoryRepository() {
        if (categoryRepository == null) {
            categoryRepository = new CategoryRepository(treeClient(), writeEngine(), categoryCodec);
        }
        return categoryRepository;
    }

    public synchronized RecordRepository recordRepository() {
        if (recordRepository == null) {
            recordRepository = new RecordRepository(
                    treeClient(), writeEngine(), linkCodec, categoryRepository());
        }
        return recordRepository;
    }

    public synchronized FileRepository fileRepository() {
        if (fileRepository == null) {
            fileRepository = new FileRepository(
                    treeClient(), writeEngine(), fileCodec, categoryRepository(), appContext.getContentResolver());
        }
        return fileRepository;
    }

    public synchronized IndexRepository indexRepository() {
        if (indexRepository == null) {
            indexRepository = new IndexRepository(
                    treeClient(), writeEngine(), recordRepository(), fileRepository());
        }
        return indexRepository;
    }

    public synchronized AutoCatRepository autoCatRepository() {
        if (autoCatRepository == null) {
            autoCatRepository = new AutoCatRepository(treeClient(), writeEngine(), autoCatCodec);
        }
        return autoCatRepository;
    }

    public synchronized BackupEngine backupEngine() {
        if (backupEngine == null) {
            backupEngine = new BackupEngine(
                    treeClient(), categoryRepository(), recordRepository(), fileRepository(),
                    categoryCodec, linkCodec, fileCodec, appContext.getContentResolver());
        }
        return backupEngine;
    }

    public synchronized RestoreEngine restoreEngine() {
        if (restoreEngine == null) {
            restoreEngine = new RestoreEngine(
                    treeClient(), writeEngine(), categoryRepository(), recordRepository(),
                    fileRepository(), indexRepository(), categoryCodec, linkCodec, fileCodec,
                    appContext.getContentResolver(), aiQueueManager());
        }
        return restoreEngine;
    }

    public synchronized LocalAiManager localAiManager() {
        if (localAiManager == null) {
            localAiManager = new LocalAiManager(appContext);
        }
        return localAiManager;
    }

    public synchronized AIQueueManager aiQueueManager() {
        if (aiQueueManager == null) {
            aiQueueManager = new AIQueueManager(appContext);
        }
        return aiQueueManager;
    }

    /** One queue, one engine: both AI tasks serialize through this. */
    public synchronized InferenceQueue inferenceQueue() {
        if (inferenceQueue == null) {
            inferenceQueue = new InferenceQueue(localAiManager()::generate);
        }
        return inferenceQueue;
    }

    public synchronized void onStorageFolderChanged() {
        treeClient = null;
        writeEngine = null;
        categoryRepository = null;
        recordRepository = null;
        fileRepository = null;
        indexRepository = null;
        autoCatRepository = null;
        backupEngine = null;
        restoreEngine = null;
    }
}
