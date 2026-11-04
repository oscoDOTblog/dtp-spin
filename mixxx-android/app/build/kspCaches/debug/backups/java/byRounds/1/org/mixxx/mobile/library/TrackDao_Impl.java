package org.mixxx.mobile.library;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TrackDao_Impl implements TrackDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TrackEntity> __insertionAdapterOfTrackEntity;

  private final EntityInsertionAdapter<CueEntity> __insertionAdapterOfCueEntity;

  public TrackDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTrackEntity = new EntityInsertionAdapter<TrackEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `tracks` (`id`,`title`,`artist`,`uri`,`durationMs`,`bpm`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TrackEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getArtist());
        statement.bindString(4, entity.getUri());
        statement.bindLong(5, entity.getDurationMs());
        if (entity.getBpm() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getBpm());
        }
      }
    };
    this.__insertionAdapterOfCueEntity = new EntityInsertionAdapter<CueEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cues` (`trackId`,`slot`,`positionMs`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CueEntity entity) {
        statement.bindLong(1, entity.getTrackId());
        statement.bindLong(2, entity.getSlot());
        statement.bindLong(3, entity.getPositionMs());
      }
    };
  }

  @Override
  public Object upsert(final TrackEntity track, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTrackEntity.insert(track);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertCue(final CueEntity cue, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfCueEntity.insert(cue);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TrackEntity>> observeTracks() {
    final String _sql = "SELECT * FROM tracks ORDER BY artist, title";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tracks"}, new Callable<List<TrackEntity>>() {
      @Override
      @NonNull
      public List<TrackEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfArtist = CursorUtil.getColumnIndexOrThrow(_cursor, "artist");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "bpm");
          final List<TrackEntity> _result = new ArrayList<TrackEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrackEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpArtist;
            _tmpArtist = _cursor.getString(_cursorIndexOfArtist);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final Double _tmpBpm;
            if (_cursor.isNull(_cursorIndexOfBpm)) {
              _tmpBpm = null;
            } else {
              _tmpBpm = _cursor.getDouble(_cursorIndexOfBpm);
            }
            _item = new TrackEntity(_tmpId,_tmpTitle,_tmpArtist,_tmpUri,_tmpDurationMs,_tmpBpm);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object cuesFor(final long trackId,
      final Continuation<? super List<CueEntity>> $completion) {
    final String _sql = "SELECT * FROM cues WHERE trackId = ? ORDER BY slot";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, trackId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<CueEntity>>() {
      @Override
      @NonNull
      public List<CueEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "trackId");
          final int _cursorIndexOfSlot = CursorUtil.getColumnIndexOrThrow(_cursor, "slot");
          final int _cursorIndexOfPositionMs = CursorUtil.getColumnIndexOrThrow(_cursor, "positionMs");
          final List<CueEntity> _result = new ArrayList<CueEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CueEntity _item;
            final long _tmpTrackId;
            _tmpTrackId = _cursor.getLong(_cursorIndexOfTrackId);
            final int _tmpSlot;
            _tmpSlot = _cursor.getInt(_cursorIndexOfSlot);
            final long _tmpPositionMs;
            _tmpPositionMs = _cursor.getLong(_cursorIndexOfPositionMs);
            _item = new CueEntity(_tmpTrackId,_tmpSlot,_tmpPositionMs);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
