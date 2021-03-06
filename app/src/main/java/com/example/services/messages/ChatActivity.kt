package com.example.services.messages

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.services.notification.NotificationData
import com.example.services.notification.PushNotification
import com.example.services.R
import com.example.services.notification.RetrofitInstance

import com.example.services.models.ChatMessage
import com.example.services.models.User
import com.example.services.shared.currentUser
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.ViewHolder
import kotlinx.android.synthetic.main.activity_chat.*

import kotlinx.android.synthetic.main.chat_from_row.view.*
import kotlinx.android.synthetic.main.chat_to_row.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val TOPIC = "/topics/myTopic"
class ChatActivity : AppCompatActivity() {
    val TAG = "ChatActivity"
    val adapter = GroupAdapter<ViewHolder>()
    lateinit var fromid:String
    lateinit var toId:String
    var myClass:User?=null
    var rcvClass:User?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        toId = intent.getStringExtra("RCV_ID")!!
        fromid = intent.getStringExtra("MY_ID")!!
        myClass = intent.getParcelableExtra("MY_CLASS")!!

        val ref = FirebaseDatabase.getInstance().getReference("/users/$toId")
        ref.keepSynced(true)
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                rcvClass = snapshot.getValue(User::class.java)!!
            }
            override fun onCancelled(error: DatabaseError) {
            }
        })

        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
        recyclerview_chatlog.adapter = adapter
        loadMessages()
        send_btn.setOnClickListener{
            if(!edittext_msg.text.isEmpty()) {
                performSendMessage()
                val userName = currentUser?.firstName + " " + currentUser?.lastName
                val title = "New Notification"
                val message = userName + " | " + edittext_msg.text.toString()
                if(title.isNotEmpty() && message.isNotEmpty()) {
                    PushNotification(
                        NotificationData(title,message),
                        rcvClass!!.token
                    ).also {
                        sendNotification(it)
                    }
                }
            }
        }
    }
    private fun sendNotification(notification: PushNotification) = CoroutineScope(Dispatchers.IO).launch {
        try {
            val response = RetrofitInstance.api.postNotification(notification)
            if(response.isSuccessful) {
                Log.d(TAG, "Response: ${Gson().toJson(response)}")
            } else {
                Log.e(TAG, response.errorBody().toString())
            }
        } catch(e: Exception) {
            Log.e(TAG, e.toString())
        }
    }
    private fun loadMessages(){
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromid/$toId")
        ref.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val chatMessage = snapshot.getValue(ChatMessage::class.java)
                if (chatMessage != null) {
                    if (chatMessage.fromId == fromid) {
                        adapter.add(ChatToItem(chatMessage.text,myClass))
                    } else if (chatMessage.fromId == toId) {
                        adapter.add(ChatFromItem(chatMessage.text,rcvClass))
                    }
                }
                recyclerview_chatlog.scrollToPosition(adapter.itemCount-1)
            }
            override fun onCancelled(error: DatabaseError) {
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
            }
        })
    }

    private fun performSendMessage(){

        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromid/$toId").push()
        val toref = FirebaseDatabase.getInstance().getReference("/user-messages/$toId/$fromid").push()
        val chatLog:String = edittext_msg.text.toString()
        if(fromid == null || toId == null)return
        val chatMessage = ChatMessage(ref.key.toString(), chatLog, fromid, toId, System.currentTimeMillis() / 1000)

        ref.setValue(chatMessage)
                .addOnSuccessListener {
                    edittext_msg.text.clear()
                    val recycleView:androidx.recyclerview.widget.RecyclerView = findViewById(R.id.recyclerview_chatlog)
                    recycleView.scrollToPosition(adapter.itemCount - 1)
                }
        toref.setValue(chatMessage)

        val latestFrom = FirebaseDatabase.getInstance().getReference("/latest-messages/$fromid/$toId")
        val latestTo = FirebaseDatabase.getInstance().getReference("/latest-messages/$toId/$fromid")

        latestFrom.setValue(chatMessage)
        latestTo.setValue(chatMessage)

    }

    class ChatFromItem(val text: String, private val rcvClass:User?): Item<ViewHolder>() {
        override fun bind(viewHolder: ViewHolder, position: Int) {
            viewHolder.itemView.textview_from_row.text = text
            if(rcvClass?.profileImgURL=="NULL"||rcvClass?.profileImgURL==null){
            }else{
                Picasso.get().load(rcvClass?.profileImgURL).into(viewHolder.itemView.imageview_chat_from_row)
            }
        }
        override fun getLayout(): Int {
            return R.layout.chat_from_row
        }
    }

    class ChatToItem(val text: String, private val myClass:User?): Item<ViewHolder>() {
        override fun bind(viewHolder: ViewHolder, position: Int) {
            viewHolder.itemView.findViewById<TextView>(R.id.textview_to_row).text = text
            if(myClass?.profileImgURL=="NULL"||myClass?.profileImgURL==null){
            }else{
                Picasso.get().load(myClass?.profileImgURL).into(viewHolder.itemView.imageview_chat_to_row)
            }
        }
        override fun getLayout(): Int {
            return R.layout.chat_to_row
        }
    }

}