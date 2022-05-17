package com.rkpandey.mymemory

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.rkpandey.mymemory.creation.CreateActivity
import com.rkpandey.mymemory.models.BoardSize
import com.rkpandey.mymemory.utils.EXTRA_BOARD_SIZE

import kotlinx.android.synthetic.main.activity_home.*

class Home : AppCompatActivity() {
  private var boardSize = BoardSize.EASY
  private val db = Firebase.firestore
  private val firebaseAnalytics = Firebase.analytics
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_home)
    nut1.setOnClickListener(){
      val intent : Intent = Intent(applicationContext, MainActivity::class.java)
      startActivity(intent)
    }
  nut2.setOnClickListener(){
    showCreationDialog()
  }

}

  private fun showCreationDialog() {
    firebaseAnalytics.logEvent("creation_show_dialog", null)
    val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
    val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroupSize)
    showAlertDialog("Tạo trò chơi cho riêng bạn", boardSizeView, View.OnClickListener {
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
      startActivityForResult(intent, MainActivity.CREATE_REQUEST_CODE)
    })
  }


  private fun showAlertDialog(title: String, view: View?, positiveClickListener: View.OnClickListener) {
    AlertDialog.Builder(this)
      .setTitle(title)
      .setView(view)
      .setNegativeButton("Đóng", null)
      .setPositiveButton("OK") { _, _ ->
        positiveClickListener.onClick(null)
      }.show()
  }

}