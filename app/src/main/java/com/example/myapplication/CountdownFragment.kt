package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class CountdownFragment : Fragment() {
    private lateinit var textObj: TextView
    private var callback: CountdownListener? =null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_countdown, container, false)
        textObj = view.findViewById(R.id.Number)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("APP", "Countdown started")
        updateText()
    }

    override fun onAttach(context: Context){
        super.onAttach(context)
        callback = context as? CountdownListener
    }

    override fun onDetach(){
        super.onDetach()
        callback = null
    }

   interface CountdownListener{
       fun countdownDone()
   }


    fun updateText() {

        lifecycleScope.launch {
            textObj.text = "1"
            delay(1000)

            textObj.text = "2"
            delay(1000)

            textObj.text = "3"
            delay(1000)

            textObj.text = "4"
            delay(1000)

            textObj.text = "5"
            delay(1000)

            callback?.countdownDone()
        }


    }
}