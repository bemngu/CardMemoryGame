package com.rkpandey.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.rkpandey.mymemory.creation.CreateActivity
import com.rkpandey.mymemory.models.BoardSize
import com.rkpandey.mymemory.models.MemoryGame
import com.rkpandey.mymemory.models.UserImageList
import com.rkpandey.mymemory.utils.EXTRA_BOARD_SIZE
import com.rkpandey.mymemory.utils.EXTRA_GAME_NAME
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_home.*

class MainActivity : AppCompatActivity() {

  companion object {
    private const val TAG = "MainActivity"
    const val CREATE_REQUEST_CODE = 248
  }

  private lateinit var clRoot: CoordinatorLayout
  private lateinit var rvBoard: RecyclerView
  private lateinit var tvNumMoves: TextView
  private lateinit var tvNumPairs: TextView

  private val db = Firebase.firestore
  private val firebaseAnalytics = Firebase.analytics
  private val remoteConfig = Firebase.remoteConfig
  private var gameName: String? = null
  private var customGameImages: List<String>? = null
  private lateinit var memoryGame: MemoryGame
  private lateinit var adapter: MemoryBoardAdapter
  private var boardSize = BoardSize.EASY

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    clRoot = findViewById(R.id.clRoot)
    rvBoard = findViewById(R.id.rvBoard)
    tvNumMoves = findViewById(R.id.tvNumMoves)
    tvNumPairs = findViewById(R.id.tvNumPairs)

    //remoteConfig.setDefaultsAsync(mapOf("about_link" to "https://www.youtube.com/rpandey1234", "scaled_height" to 250L, "compress_quality" to 60L))
    remoteConfig.fetchAndActivate()
      .addOnCompleteListener(this) { task ->
        if (task.isSuccessful) {
          Log.i(TAG, "K??ch ho???t th??nh c??ng c???u h??nh ${task.result}")
        } else {
          Log.w(TAG, "C???p nh???p c???u h??nh t??? xa kh??ng th??nh c??ng")
        }
      }
    setupBoard()
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.mi_refresh -> {
        if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
          showAlertDialog("B???n c?? mu???n ch??i l???i game?", null, View.OnClickListener {
            setupBoard()
          })
        } else {
          setupBoard()
        }
        return true
      }
      R.id.back ->{
        val intent : Intent = Intent(applicationContext, Home::class.java)
        startActivity(intent)
      }
      R.id.mi_new_size -> {
        showNewSizeDialog()
        return true
      }
      R.id.mi_custom -> {
        showCreationDialog()
        return true
      }


    }
    return super.onOptionsItemSelected(item)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
      val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
      if (customGameName == null) {
        Log.e(TAG, "Tr?? ch??i t??y ch???nh t??? CreateActivity")
        return
      }
      downloadGame(customGameName)
    }
    super.onActivityResult(requestCode, resultCode, data)
  }



  private fun downloadGame(customGameName: String) {
    if (customGameName.isBlank()) {
      Snackbar.make(clRoot, "T??n tr?? ch??i kh??ng ???????c ????? tr???ng", Snackbar.LENGTH_LONG).show()
      Log.e(TAG, "??ang c??? g???ng truy xu???t tr?? ch??i tr???ng")
      return
    }
    firebaseAnalytics.logEvent("download_game_attempt") {
      param("game_name", customGameName)
    }
    db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
      val userImageList = document.toObject(UserImageList::class.java)
      if (userImageList?.images == null)   {
        Log.e(TAG, "D??? li???u tr?? ch??i t??y ch???nh kh??ng h???p l??? t??? Firebase")
        Snackbar.make(clRoot, "Xin l???i, ch??ng t??i kh??ng th??? t??m th???y b???t k??? tr?? ch??i n??o nh?? v???y, '$customGameName'", Snackbar.LENGTH_LONG).show()
        return@addOnSuccessListener
      }
      firebaseAnalytics.logEvent("download_game_success") {
        param("game_name", customGameName)
      }
      val numCards = userImageList.images.size * 2
      boardSize = BoardSize.getByValue(numCards)
      customGameImages = userImageList.images
      gameName = customGameName
      // Pre-fetch the images for faster loading
      for (imageUrl in userImageList.images) {
        Picasso.get().load(imageUrl).fetch()
      }
      Snackbar.make(clRoot, "B???n ??ang ch??i '$customGameName'!", Snackbar.LENGTH_LONG).show()
      setupBoard()
    }.addOnFailureListener { exception ->
      Log.e(TAG, "Exception when retrieving game", exception)
    }
  }

  private fun showCreationDialog() {
    firebaseAnalytics.logEvent("creation_show_dialog", null)
    val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
    val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroupSize)
    showAlertDialog("T???o b??? nh??? c???a b???n", boardSizeView, View.OnClickListener {
      val desiredBoardSize = when (radioGroupSize.checkedRadioButtonId) {
        R.id.rbEasy -> BoardSize.EASY
        R.id.rbMedium -> BoardSize.MEDIUM
        else -> BoardSize.HARD
      }
      firebaseAnalytics.logEvent("creation_start_activity") {
        param("board_size", desiredBoardSize.name)
      }
      val intent = Intent(this, CreateActivity::class.java)
      intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
      startActivityForResult(intent, CREATE_REQUEST_CODE)
    })
  }

  private fun showNewSizeDialog() {
    val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
    val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroupSize)
    when (boardSize) {
      BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
      BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
      BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
    }
    showAlertDialog("Ch???n k??ch th?????c m???i", boardSizeView, View.OnClickListener {
      boardSize = when (radioGroupSize.checkedRadioButtonId) {
        R.id.rbEasy -> BoardSize.EASY
        R.id.rbMedium -> BoardSize.MEDIUM
        else -> BoardSize.HARD
      }
      gameName = null
      customGameImages = null
      setupBoard()
    })
  }

  private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
    AlertDialog.Builder(this)
      .setTitle(title)
      .setView(view)
      .setNegativeButton("????ng", null)
      .setPositiveButton("OK") { _, _ ->
        positiveClickListener.onClick(null)
      }.show()
  }

  private fun setupBoard() {
    supportActionBar?.title = gameName ?: getString(R.string.app_name)
    memoryGame = MemoryGame(boardSize, customGameImages)
    when (boardSize) {
      BoardSize.EASY -> {
        tvNumMoves.text = "D???: 4 x 2"
        tvNumPairs.text = "Ho??n th??nh: 0/4"
      }
      BoardSize.MEDIUM -> {
        tvNumMoves.text = "Trung b??nh: 6 x 3"
        tvNumPairs.text = "Ho??n th??nh: 0/9"
      }
      BoardSize.HARD -> {
        tvNumMoves.text = "Kh??: 6 x 4"
        tvNumPairs.text = "Ho??n th??nh: 0/12"
      }
    }
    tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
    adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener {
      override fun onCardClicked(position: Int) {
        updateGameWithFlip(position)
      }
    })
    rvBoard.adapter = adapter
    rvBoard.setHasFixedSize(true)
    rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
  }

  private fun updateGameWithFlip(position: Int) {
    // Error handling:
    if (memoryGame.haveWonGame()) {
      Snackbar.make(clRoot, "Ch??c m???ng b???n ???? chi???n th???ng", Snackbar.LENGTH_LONG).show()
      return
    }
    if (memoryGame.isCardFaceUp(position)) {
      Snackbar.make(clRoot, "L???n ch??i kh??ng h???p l???", Snackbar.LENGTH_SHORT).show()
      return
    }

    // Actually flip the card
    if (memoryGame.flipCard(position)) {
      Log.i(TAG, "T??m th???y m???t tr???n ?????u! S??? c???p ???????c t??m th???y: ${memoryGame.numPairsFound}")
      val color = ArgbEvaluator().evaluate(
        memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
        ContextCompat.getColor(this, R.color.color_progress_none),
        ContextCompat.getColor(this, R.color.color_progress_full)
      ) as Int
      tvNumPairs.setTextColor(color)
      tvNumPairs.text = "S??? c???p: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
      if (memoryGame.haveWonGame()) {
        Snackbar.make(clRoot, "Th???ng r???i, ch??c m???ng nha!", Snackbar.LENGTH_LONG).show()
        CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
        firebaseAnalytics.logEvent("won_game") {
          param("game_name", gameName ?: "[default]")
          param("board_size", boardSize.name)
        }
      }
    }
    tvNumMoves.text = "L???n ch???n: ${memoryGame.getNumMoves()}"
    adapter.notifyDataSetChanged()
  }
}